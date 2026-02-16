package com.github.chbndrhnns.betterpy.features.intentions.movescope

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class PyMoveMethodToTopLevelProcessor(
    private val project: Project,
    private val method: PyFunction
) {
    fun run() {
        val file = method.containingFile as? PyFile ?: return
        val containingClass = method.containingClass ?: return
        val methodName = method.name ?: return
        val className = containingClass.name ?: return

        val movedFuncText = buildMovedFunctionText(className)

        // Collect call site edits: obj.method(args) → method(obj, args)
        val callSiteEdits = collectCallSiteEdits(methodName, containingClass)

        val psiDocManager = PsiDocumentManager.getInstance(project)
        val document = psiDocManager.getDocument(file) ?: return
        psiDocManager.commitDocument(document)

        val text = document.text

        // Determine what to remove: decorators + method
        val removeStartElement = method.decoratorList ?: method
        val removeStartOffset = removeStartElement.textRange.startOffset
        val removeEndOffset = method.textRange.endOffset

        // Find the start of the line
        val lineStart = text.lastIndexOf('\n', removeStartOffset - 1)
        val removeStart = if (lineStart >= 0) lineStart + 1 else 0

        var removeEnd = removeEndOffset
        if (removeEnd < text.length && text[removeEnd] == '\n') removeEnd++

        // Check if the class body will be empty after removal
        val statements = containingClass.statementList.statements
        val needsPass = statements.size == 1 && statements[0] === method

        // Apply edits from bottom to top
        val allEdits = mutableListOf<TextEdit>()
        allEdits.addAll(callSiteEdits)

        if (needsPass) {
            // Replace method with pass (keeping indentation)
            val indent = removeStartOffset - (lineStart + 1)
            allEdits.add(TextEdit(removeStart, removeEnd, " ".repeat(indent) + "pass\n"))
        } else {
            allEdits.add(TextEdit(removeStart, removeEnd, ""))
        }

        allEdits.sortByDescending { it.startOffset }

        val sb = StringBuilder(text)
        for (edit in allEdits) {
            sb.replace(edit.startOffset, edit.endOffset, edit.replacement)
        }

        val bodyText = sb.toString().trimEnd()
        val finalText = "$bodyText\n\n$movedFuncText\n"
        document.setText(finalText)
        psiDocManager.commitDocument(document)
    }

    private fun buildMovedFunctionText(className: String): String {
        val fileText = method.containingFile.text
        val startElement = method.decoratorList ?: method
        val startOffset = startElement.textRange.startOffset
        val endOffset = method.textRange.endOffset

        val dedented = MoveScopeTextBuilder.reindentRange(fileText, startOffset, endOffset, 0)

        // Replace `self` parameter with class-typed parameter
        return replaceSelfParameter(dedented, className)
    }

    private fun replaceSelfParameter(funcText: String, className: String): String {
        // Match the def line to find and replace self parameter
        val defMatch = Regex("""^(\s*(?:async\s+)?def\s+\w+\s*\()([^)]*)\)""").find(funcText)
            ?: return funcText

        val prefix = defMatch.groupValues[1]
        val params = defMatch.groupValues[2]

        // Split params and replace self
        val paramList = params.split(",").map { it.trim() }.toMutableList()
        if (paramList.isEmpty()) return funcText

        val firstParam = paramList[0]
        if (firstParam == "self") {
            paramList[0] = "self: $className"
        } else if (firstParam.startsWith("self:")) {
            // Already typed, keep as-is
        } else {
            // Not a self parameter (e.g., @staticmethod) — just dedent, no param change
            return funcText
        }

        val newParams = paramList.joinToString(", ")
        return funcText.replaceRange(defMatch.range, "$prefix$newParams)")
    }

    private fun collectCallSiteEdits(methodName: String, containingClass: PyClass): List<TextEdit> {
        val edits = mutableListOf<TextEdit>()
        val file = method.containingFile

        // Find all calls like obj.method(...) or ClassName.method(obj, ...) in the file
        val calls = PsiTreeUtil.findChildrenOfType(file, PyCallExpression::class.java)
        for (call in calls) {
            // Skip calls inside the method being moved
            if (PsiTreeUtil.isAncestor(method, call, true)) continue

            val callee = call.callee as? PyReferenceExpression ?: continue
            if (callee.referencedName != methodName) continue
            val qualifier = callee.qualifier ?: continue

            // Check if qualifier resolves to an instance of the containing class or the class itself
            // For simplicity, we transform any qualified call `X.method(args)` where method name matches
            val qualifierText = qualifier.text

            // Transform: qualifier.method(args) → method(qualifier, args)
            val callStart = call.textRange.startOffset
            val callEnd = call.textRange.endOffset

            // Build new call text: method(qualifier, existing_args)
            val existingArgs = call.arguments.joinToString(", ") { it.text }
            val newCallText = if (existingArgs.isEmpty()) {
                "$methodName($qualifierText)"
            } else {
                "$methodName($qualifierText, $existingArgs)"
            }

            edits.add(TextEdit(callStart, callEnd, newCallText))
        }
        return edits
    }

    private data class TextEdit(val startOffset: Int, val endOffset: Int, val replacement: String)
}
