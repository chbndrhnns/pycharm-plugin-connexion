package com.github.chbndrhnns.betterpy.features.intentions.movescope

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile

class PyMoveToOuterScopeProcessor(
    private val project: Project,
    private val nestedClass: PyClass
) {
    fun run() {
        val outerClass = PsiTreeUtil.getParentOfType(nestedClass, PyClass::class.java) ?: return
        val file = nestedClass.containingFile as? PyFile ?: return
        val nestedName = nestedClass.name ?: return
        val outerName = outerClass.name ?: return

        // Determine if we're moving to top-level or to a grandparent class
        val grandparentClass = PsiTreeUtil.getParentOfType(outerClass, PyClass::class.java, true)
        val movingToTopLevel = grandparentClass == null

        val movedClassText = buildMovedClassText(nestedClass, grandparentClass)
        val qualifiedRef = "$outerName.$nestedName"

        // Determine what to remove: decorators + class
        val removeStartElement = nestedClass.decoratorList ?: nestedClass
        val removeStartOffset = removeStartElement.textRange.startOffset
        val removeEndOffset = nestedClass.textRange.endOffset

        // Check if the outer class body will be empty after removal
        val statements = outerClass.statementList.statements
        val needsPass = statements.size == 1 && statements[0] === nestedClass

        // Remember the outer class end offset before any text changes
        val outerClassEndOffset = outerClass.textRange.endOffset

        val psiDocManager = PsiDocumentManager.getInstance(project)
        val document = psiDocManager.getDocument(file) ?: return
        psiDocManager.commitDocument(document)

        val text = document.text

        // 1. Update references (Outer.Inner -> Inner)
        val newText = text.replace(qualifiedRef, nestedName)

        // 2. Remove the nested class (including leading whitespace on its line)
        var removeStart = removeStartOffset
        while (removeStart > 0 && newText[removeStart - 1] == ' ') removeStart--
        if (removeStart > 0 && newText[removeStart - 1] == '\n') removeStart--

        var removeEnd = removeEndOffset
        if (removeEnd < newText.length && newText[removeEnd] == '\n') removeEnd++

        val beforeRemoval = newText.substring(0, removeStart)
        val afterRemoval = newText.substring(removeEnd)

        val middlePart = if (needsPass) {
            val lineStart = newText.lastIndexOf('\n', removeStartOffset - 1) + 1
            val indent = removeStartOffset - lineStart
            "\n" + " ".repeat(indent) + "pass"
        } else {
            ""
        }

        // 3. Build the new file text
        if (movingToTopLevel) {
            val bodyText = (beforeRemoval + middlePart + afterRemoval).trimEnd()
            val finalText = "$bodyText\n\n$movedClassText\n"
            document.setText(finalText)
        } else {
            // For doubly-nested: insert as sibling of outerClass within grandparent.
            // We need to find where outerClass ends in the modified text.
            val bodyText = beforeRemoval + middlePart + afterRemoval

            // Calculate how much text was removed/changed before outerClassEndOffset
            val refLenDiff = qualifiedRef.length - nestedName.length
            var refsBeforeOuterEnd = 0
            var searchFrom = 0
            while (true) {
                val idx = text.indexOf(qualifiedRef, searchFrom)
                if (idx == -1 || idx >= outerClassEndOffset) break
                refsBeforeOuterEnd++
                searchFrom = idx + qualifiedRef.length
            }
            val refShift = refsBeforeOuterEnd * refLenDiff

            // The removal happened within the outer class, so the outer class end shifts
            val removedLength = removeEnd - removeStart
            val addedLength = middlePart.length
            val removalShift = removedLength - addedLength

            val adjustedOuterEnd = outerClassEndOffset - refShift - removalShift

            // Clamp to bodyText length
            val insertPoint = adjustedOuterEnd.coerceIn(0, bodyText.length)

            val finalText = bodyText.substring(0, insertPoint) + "\n\n" + movedClassText + bodyText.substring(insertPoint).trimEnd() + "\n"
            document.setText(finalText)
        }
        psiDocManager.commitDocument(document)
    }

    private fun buildMovedClassText(pyClass: PyClass, grandparentClass: PyClass?): String {
        val file = pyClass.containingFile
        val startElement = pyClass.decoratorList ?: pyClass
        val startOffset = startElement.textRange.startOffset
        val endOffset = pyClass.textRange.endOffset
        val fileText = file.text

        val targetIndent = if (grandparentClass != null) {
            // Indent to the level of the outer class (sibling of outerClass within grandparent)
            val outerClass = PsiTreeUtil.getParentOfType(pyClass, PyClass::class.java) ?: return ""
            MoveScopeTextBuilder.elementLineIndent(outerClass.decoratorList ?: outerClass)
        } else {
            0
        }

        return MoveScopeTextBuilder.reindentRange(fileText, startOffset, endOffset, targetIndent)
    }
}
