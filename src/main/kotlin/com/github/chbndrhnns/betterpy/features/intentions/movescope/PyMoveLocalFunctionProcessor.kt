package com.github.chbndrhnns.betterpy.features.intentions.movescope

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReferenceExpression

class PyMoveLocalFunctionProcessor(
    private val project: Project,
    private val localFunc: PyFunction,
    private val outerFunc: PyFunction
) {
    fun run() {
        val file = localFunc.containingFile as? PyFile ?: return
        val funcName = localFunc.name ?: return

        val capturedVars = PyMoveToOuterScopeIntention.findCapturedVariables(localFunc, outerFunc)

        // Build the moved function text (dedented to top-level)
        val movedFuncText = buildMovedFunctionText(capturedVars)

        // Collect call site offsets before modifying text
        val callSiteEdits = collectCallSiteEdits(funcName, capturedVars)

        val psiDocManager = PsiDocumentManager.getInstance(project)
        val document = psiDocManager.getDocument(file) ?: return
        psiDocManager.commitDocument(document)

        val text = document.text

        // Determine what to remove: decorators + function
        val removeStartElement = localFunc.decoratorList ?: localFunc
        val removeStartOffset = removeStartElement.textRange.startOffset
        val removeEndOffset = localFunc.textRange.endOffset

        // Find the start of the line containing the function (character after the preceding newline)
        val lineStart = text.lastIndexOf('\n', removeStartOffset - 1)
        val removeStart = if (lineStart >= 0) lineStart + 1 else 0

        var removeEnd = removeEndOffset
        // Also consume the trailing newline if present
        if (removeEnd < text.length && text[removeEnd] == '\n') removeEnd++

        // Apply edits from bottom to top (reverse order by offset) to preserve offsets
        val allEdits = mutableListOf<TextEdit>()

        // Add call site edits (insert captured vars as extra args)
        allEdits.addAll(callSiteEdits)

        // Add removal of the local function
        allEdits.add(TextEdit(removeStart, removeEnd, ""))

        // Sort edits by offset descending so we can apply them without shifting
        allEdits.sortByDescending { it.startOffset }

        val sb = StringBuilder(text)
        for (edit in allEdits) {
            sb.replace(edit.startOffset, edit.endOffset, edit.replacement)
        }

        // Append the moved function at the end
        val bodyText = sb.toString().trimEnd()
        val finalText = "$bodyText\n\n$movedFuncText\n"
        document.setText(finalText)
        psiDocManager.commitDocument(document)
    }

    private fun buildMovedFunctionText(capturedVars: List<String>): String {
        val fileText = localFunc.containingFile.text
        val startElement = localFunc.decoratorList ?: localFunc
        val startOffset = startElement.textRange.startOffset
        val endOffset = localFunc.textRange.endOffset

        val dedented = MoveScopeTextBuilder.reindentRange(fileText, startOffset, endOffset, 0)

        // If there are captured variables, add them as parameters
        if (capturedVars.isEmpty()) return dedented

        return addParametersToFunction(dedented, capturedVars)
    }

    private fun addParametersToFunction(funcText: String, capturedVars: List<String>): String {
        // Find the parameter list in the function definition
        val defMatch = Regex("""^(\s*(?:async\s+)?def\s+\w+\s*\()([^)]*)\)""").find(funcText)
            ?: return funcText

        val prefix = defMatch.groupValues[1]
        val existingParams = defMatch.groupValues[2]
        val newParams = if (existingParams.isBlank()) {
            capturedVars.joinToString(", ")
        } else {
            existingParams + ", " + capturedVars.joinToString(", ")
        }

        return funcText.replaceRange(defMatch.range, "$prefix$newParams)")
    }

    private fun collectCallSiteEdits(funcName: String, capturedVars: List<String>): List<TextEdit> {
        if (capturedVars.isEmpty()) return emptyList()

        val edits = mutableListOf<TextEdit>()
        val calls = PsiTreeUtil.findChildrenOfType(outerFunc, PyCallExpression::class.java)
        for (call in calls) {
            // Skip calls inside the local function itself
            if (PsiTreeUtil.isAncestor(localFunc, call, true)) continue
            val callee = call.callee as? PyReferenceExpression ?: continue
            if (callee.referencedName != funcName) continue

            // Find the closing paren of the call
            val closeParenOffset = call.textRange.endOffset - 1 // position of ')'

            // Build the extra args text
            val extraArgs = capturedVars.joinToString(", ")
            val existingArgs = call.arguments
            val insertText = if (existingArgs.isEmpty()) extraArgs else ", $extraArgs"

            edits.add(TextEdit(closeParenOffset, closeParenOffset, insertText))
        }
        return edits
    }

    private data class TextEdit(val startOffset: Int, val endOffset: Int, val replacement: String)
}
