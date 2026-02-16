package com.github.chbndrhnns.betterpy.features.intentions.movescope

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class PyMoveFunctionIntoClassProcessor(
    private val project: Project,
    private val function: PyFunction,
    private val targetClass: PyClass,
    private val updateCallSites: Boolean = true,
    private val methodStyle: MoveScopeTextBuilder.MethodStyle = MoveScopeTextBuilder.MethodStyle.INSTANCE_IF_FIRST_PARAM_TYPED
) {
    fun run() {
        val file = function.containingFile as? PyFile ?: return
        val funcName = function.name ?: return
        val className = targetClass.name ?: return

        // Build the method text (indented into the class)
        val methodText = MoveScopeTextBuilder.buildMethodText(function, targetClass, methodStyle)
        val isInstanceMethod = MoveScopeTextBuilder.isInstanceMethod(function, className, methodStyle)

        // Collect call site edits: func(obj, args) → obj.func(args) for instance methods
        val callSiteEdits = if (updateCallSites) collectCallSiteEdits(funcName, isInstanceMethod) else emptyList()

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
