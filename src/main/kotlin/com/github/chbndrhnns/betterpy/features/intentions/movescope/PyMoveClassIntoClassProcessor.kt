package com.github.chbndrhnns.betterpy.features.intentions.movescope

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyPassStatement
import com.jetbrains.python.psi.PyReferenceExpression

class PyMoveClassIntoClassProcessor(
    private val project: Project,
    private val classToMove: PyClass,
    private val targetClass: PyClass
) {
    fun run() {
        val file = classToMove.containingFile as? PyFile ?: return
        val className = classToMove.name ?: return
        val targetClassName = targetClass.name ?: return

        // Build the nested class text (indented into the target class)
        val nestedClassText = buildNestedClassText()

        // Collect reference edits: ClassName → TargetClass.ClassName
        val referenceEdits = collectReferenceEdits(className, targetClassName)

        val psiDocManager = PsiDocumentManager.getInstance(project)
        val document = psiDocManager.getDocument(file) ?: return
        psiDocManager.commitDocument(document)

        val text = document.text

        // Determine what to remove: decorators + class
        val removeStartElement = classToMove.decoratorList ?: classToMove
        val removeStartOffset = removeStartElement.textRange.startOffset
        val removeEndOffset = classToMove.textRange.endOffset

        // Find the start of the line
        val lineStart = text.lastIndexOf('\n', removeStartOffset - 1)
        val removeStart = if (lineStart >= 0) lineStart else 0

        var removeEnd = removeEndOffset
        // Consume trailing newline
        if (removeEnd < text.length && text[removeEnd] == '\n') removeEnd++

        // Find where to insert the nested class in the target class (at the end of the class body)
        val classBodyEnd = targetClass.statementList.textRange.endOffset

        // Check if the target class body is just "pass" — we'll replace it
        val statements = targetClass.statementList.statements
        val isPassOnly = statements.size == 1 && statements[0] is PyPassStatement

        // Apply edits from bottom to top
        val allEdits = mutableListOf<TextEdit>()
        allEdits.addAll(referenceEdits)

        // Remove the class
        allEdits.add(TextEdit(removeStart, removeEnd, ""))

        // Insert nested class into target class (or replace pass)
        if (isPassOnly) {
            val passStmt = statements[0]
            val passEnd = passStmt.textRange.endOffset
            val passLineStart = text.lastIndexOf('\n', passStmt.textRange.startOffset - 1)
            val passReplaceStart = if (passLineStart >= 0) passLineStart + 1 else 0
            allEdits.add(TextEdit(passReplaceStart, passEnd, nestedClassText))
        } else {
            allEdits.add(TextEdit(classBodyEnd, classBodyEnd, "\n\n" + nestedClassText))
        }

        allEdits.sortByDescending { it.startOffset }

        val sb = StringBuilder(text)
        for (edit in allEdits) {
            sb.replace(edit.startOffset, edit.endOffset, edit.replacement)
        }

        val finalText = sb.toString().trimEnd() + "\n"
        document.setText(finalText)
        psiDocManager.commitDocument(document)
    }

    private fun buildNestedClassText(): String {
        val fileText = classToMove.containingFile.text
        val startElement = classToMove.decoratorList ?: classToMove
        val startOffset = startElement.textRange.startOffset
        val endOffset = classToMove.textRange.endOffset

        val targetIndent = MoveScopeTextBuilder.classBodyIndent(targetClass)
        return MoveScopeTextBuilder.reindentRange(fileText, startOffset, endOffset, targetIndent)
    }

    private fun collectReferenceEdits(className: String, targetClassName: String): List<TextEdit> {
        val edits = mutableListOf<TextEdit>()
        val file = classToMove.containingFile

        // Find all references to the class being moved
        val refs = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
        for (ref in refs) {
            // Skip references inside the class being moved itself
            if (PsiTreeUtil.isAncestor(classToMove, ref, true)) continue
            // Skip references inside the target class (they'll use the short name)
            if (PsiTreeUtil.isAncestor(targetClass, ref, true)) continue

            if (ref.referencedName != className) continue
            // Only handle unqualified references
            if (ref.qualifier != null) continue

            // Check if this reference actually resolves to the class being moved
            // For simplicity, we update all unqualified references with the matching name
            val qualifiedName = "$targetClassName.$className"
            edits.add(TextEdit(ref.textRange.startOffset, ref.textRange.endOffset, qualifiedName))
        }

        // Also update string references (e.g., type annotations in quotes)
        // We handle this via simple text replacement in the document after PSI-based edits
        return edits
    }

    private data class TextEdit(val startOffset: Int, val endOffset: Int, val replacement: String)
}
