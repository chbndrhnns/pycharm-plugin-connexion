package com.github.chbndrhnns.betterpy.features.intentions.movescope

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class PyMoveFunctionIntoClassProcessor(
    private val project: Project,
    private val function: PyFunction,
    private val targetClass: PyClass
) {
    fun run() {
        val file = function.containingFile as? PyFile ?: return
        val funcName = function.name ?: return
        val className = targetClass.name ?: return

        // Determine if first param is typed as the target class → instance method
        val isInstanceMethod = isFirstParamTypedAsClass(function, className)

        // Build the method text (indented into the class)
        val methodText = buildMethodText(isInstanceMethod, className)

        // Collect call site edits: func(obj, args) → obj.func(args) for instance methods
        val callSiteEdits = collectCallSiteEdits(funcName, isInstanceMethod)

        val psiDocManager = PsiDocumentManager.getInstance(project)
        val document = psiDocManager.getDocument(file) ?: return
        psiDocManager.commitDocument(document)

        val text = document.text

        // Determine what to remove: decorators + function
        val removeStartElement = function.decoratorList ?: function
        val removeStartOffset = removeStartElement.textRange.startOffset
        val removeEndOffset = function.textRange.endOffset

        // Find the start of the line
        val lineStart = text.lastIndexOf('\n', removeStartOffset - 1)
        val removeStart = if (lineStart >= 0) lineStart else 0

        var removeEnd = removeEndOffset
        // Consume trailing newline (but keep blank line separators)
        if (removeEnd < text.length && text[removeEnd] == '\n') removeEnd++

        // Find where to insert the method in the target class (at the end of the class body)
        val classBodyEnd = targetClass.statementList.textRange.endOffset

        // Check if the class body is just "pass" — we'll replace it
        val statements = targetClass.statementList.statements
        val isPassOnly = statements.size == 1 && statements[0] is PyPassStatement

        // Apply edits from bottom to top
        val allEdits = mutableListOf<TextEdit>()
        allEdits.addAll(callSiteEdits)

        // Remove the function
        allEdits.add(TextEdit(removeStart, removeEnd, ""))

        // Insert method into class (or replace pass)
        if (isPassOnly) {
            // Replace the entire "pass" line (including leading whitespace) with the method text
            val passStmt = statements[0]
            val passEnd = passStmt.textRange.endOffset
            // Find the start of the line containing pass
            val passLineStart = text.lastIndexOf('\n', passStmt.textRange.startOffset - 1)
            val passReplaceStart = if (passLineStart >= 0) passLineStart + 1 else 0
            allEdits.add(TextEdit(passReplaceStart, passEnd, methodText))
        } else {
            // Insert after the last statement in the class body
            allEdits.add(TextEdit(classBodyEnd, classBodyEnd, "\n\n" + methodText))
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

    private fun isFirstParamTypedAsClass(func: PyFunction, className: String): Boolean {
        val params = func.parameterList.parameters
        if (params.isEmpty()) return false
        val firstParam = params[0] as? PyNamedParameter ?: return false
        val annotationText = firstParam.annotation?.value?.text ?: return false
        return annotationText == className
    }

    private fun buildMethodText(isInstanceMethod: Boolean, className: String): String {
        val fileText = function.containingFile.text
        val startElement = function.decoratorList ?: function
        val startOffset = startElement.textRange.startOffset
        val endOffset = function.textRange.endOffset

        // Compute current indentation (should be 0 for top-level)
        val lineStart = fileText.lastIndexOf('\n', startOffset - 1) + 1
        val currentIndent = startOffset - lineStart

        // Compute target indentation (inside the class body)
        val classBodyStart = targetClass.statementList.textRange.startOffset
        val classBodyLineStart = fileText.lastIndexOf('\n', classBodyStart - 1) + 1
        val targetIndent = classBodyStart - classBodyLineStart

        // Extract raw text and re-indent
        val rawText = fileText.substring(lineStart, endOffset)
        val lines = rawText.lines()
        val reindented = lines.joinToString("\n") { line ->
            if (line.isBlank()) ""
            else {
                val stripped = if (line.length >= currentIndent) line.drop(currentIndent) else line.trimStart()
                " ".repeat(targetIndent) + stripped
            }
        }

        if (isInstanceMethod) {
            // Replace first param (typed as ClassName) with self
            return replaceFirstParamWithSelf(reindented, className)
        } else {
            // Add @staticmethod decorator
            return " ".repeat(targetIndent) + "@staticmethod\n" + reindented
        }
    }

    private fun replaceFirstParamWithSelf(funcText: String, className: String): String {
        // Match the def line to find and replace the first parameter
        val defMatch = Regex("""^(\s*(?:async\s+)?def\s+\w+\s*\()([^)]*)\)""").find(funcText)
            ?: return funcText

        val prefix = defMatch.groupValues[1]
        val params = defMatch.groupValues[2]

        // Split params and replace first one with self
        val paramList = params.split(",").map { it.trim() }.toMutableList()
        if (paramList.isEmpty()) return funcText

        // First param should be like "name: ClassName" — replace with "self"
        paramList[0] = "self"

        val newParams = paramList.joinToString(", ")
        return funcText.replaceRange(defMatch.range, "$prefix$newParams)")
    }

    private fun collectCallSiteEdits(funcName: String, isInstanceMethod: Boolean): List<TextEdit> {
        val edits = mutableListOf<TextEdit>()
        val file = function.containingFile

        val calls = PsiTreeUtil.findChildrenOfType(file, PyCallExpression::class.java)
        for (call in calls) {
            // Skip calls inside the function being moved
            if (PsiTreeUtil.isAncestor(function, call, true)) continue

            val callee = call.callee as? PyReferenceExpression ?: continue
            if (callee.referencedName != funcName) continue
            // Only handle unqualified calls (top-level function calls)
            if (callee.qualifier != null) continue

            if (isInstanceMethod) {
                // Transform: func(obj, args) → obj.func(args)
                val args = call.arguments
                if (args.isEmpty()) continue // shouldn't happen for instance method

                val firstArgText = args[0].text
                val remainingArgs = args.drop(1).joinToString(", ") { it.text }
                val newCallText = if (remainingArgs.isEmpty()) {
                    "$firstArgText.$funcName()"
                } else {
                    "$firstArgText.$funcName($remainingArgs)"
                }

                edits.add(TextEdit(call.textRange.startOffset, call.textRange.endOffset, newCallText))
            }
            // For static methods, call sites don't change (function is still called the same way,
            // but now as ClassName.func(...) — however, for simplicity in single-file scope,
            // we update to ClassName.func(...)
            else {
                val className = targetClass.name ?: continue
                val argsText = call.arguments.joinToString(", ") { it.text }
                val newCallText = "$className.$funcName($argsText)"
                edits.add(TextEdit(call.textRange.startOffset, call.textRange.endOffset, newCallText))
            }
        }
        return edits
    }

    private data class TextEdit(val startOffset: Int, val endOffset: Int, val replacement: String)
}
