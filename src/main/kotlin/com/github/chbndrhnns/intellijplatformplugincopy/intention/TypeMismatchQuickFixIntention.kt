package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Offers a quick-fix intention when the caret is on a highlight that reports
 * an "expected type vs actual type" mismatch from specific inspections.
 *
 */
class TypeMismatchQuickFixIntention : IntentionAction, HighPriorityAction, DumbAware {

    private var typeMismatchDetails: TypeMismatchDetails? = null

    private val supportedInspectionIds = setOf(
        "PyTypeCheckerInspection"
    )

    override fun getText(): String = "Show type mismatch details"

    override fun getFamilyName(): String = "Type mismatch helper"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        typeMismatchDetails = null

        // First check if we have a type mismatch highlight at the current position
        if (!hasTypeMismatchHighlightAtCaret(project, editor)) {
            return false
        }

        // Try to compute type mismatch details using Python type system
        val element = findElementAtCaret(editor, file)
        if (element is PyExpression) {
            val details = computeTypeMismatchDetails(element, project, file)
            if (details != null) {
                typeMismatchDetails = details
                return true
            }
        }

        return false
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val details = typeMismatchDetails
        if (details != null) {
            val message = "Expected: ${details.expectedType}, Actual: ${details.actualType}"
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(
                    "Type Mismatch Details",
                    message,
                    NotificationType.INFORMATION
                )
                .notify(project)
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val details = typeMismatchDetails

        return if (details != null) {
            // Create a rich HTML preview showing the type mismatch details
            val htmlContent = buildString {
                append("<html><body>")
                append("<div style='padding: 8px; font-family: monospace;'>")
                append("<p><strong>Type Mismatch Details:</strong></p>")
                append("<div>Expected: <span style='color: #0D7377; font-weight: bold;'>${details.expectedType}</span></div>")
                append("<div>Actual: <span style='color: #FF6B6B; font-weight: bold;'>${details.actualType}</span></div>")
                if (details.context.isNotEmpty()) {
                    append("<div>Context: <span style='font-style: italic;'>${details.context}</span></div>")
                }
                append("</div>")
                append("</body></html>")
            }

            IntentionPreviewInfo.Html(htmlContent)
        } else {
            IntentionPreviewInfo.EMPTY
        }
    }

    override fun startInWriteAction(): Boolean = false

    /**
     * Data class to hold parsed type mismatch information
     */
    private data class TypeMismatchDetails(
        val expectedType: String,
        val actualType: String,
        val context: String = ""
    )

    /**
     * Checks if there's a type mismatch highlight at the current caret position.
     */
    private fun hasTypeMismatchHighlightAtCaret(project: Project, editor: Editor): Boolean {
        val offset = editor.caretModel.offset
        val document = editor.document

        val mm = (DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx) ?: return false

        // Process very small range around caret to keep it efficient
        val start = maxOf(0, offset - 1)
        val end = minOf(document.textLength, offset + 1)

        var found = false
        mm.processRangeHighlightersOverlappingWith(start, end) { h ->
            if (h.isValid && h.targetArea == HighlighterTargetArea.EXACT_RANGE && h.errorStripeTooltip != null) {
                if (isCaretInside(h, offset) && isFromSupportedInspection(h)) {
                    val tooltipText = h.errorStripeTooltip?.toString() ?: ""
                    if (isTypeMismatchMessage(tooltipText.lowercase())) {
                        found = true
                        return@processRangeHighlightersOverlappingWith false // Stop processing
                    }
                }
            }
            true
        }

        return found
    }

    /**
     * Computes type mismatch details using the Python type system APIs.
     */
    private fun computeTypeMismatchDetails(
        element: PyExpression,
        project: Project,
        file: PsiFile
    ): TypeMismatchDetails? {
        try {
            val context = TypeEvalContext.codeAnalysis(project, file)
            val (actualType, expectedType) = computeTypeInfo(element, context)

            if (actualType != null && expectedType != null && actualType != expectedType) {
                val contextInfo = extractContextInfo(element)
                return TypeMismatchDetails(expectedType, actualType, contextInfo)
            }

        } catch (_: Exception) {
        }

        return null
    }

    private fun computeTypeInfo(expression: PyExpression, context: TypeEvalContext): Pair<String?, String?> {
        // Use PyTypeProvider extension point approach - compute actual and expected types
        val actualType = context.getType(expression)
        val expectedType = getExpectedTypeFromContext(expression, context)

        val actualTypeName = actualType?.name ?: "Unknown"
        val expectedTypeName = expectedType?.name ?: "Unknown"

        return Pair(actualTypeName, expectedTypeName)
    }

    private fun getExpectedTypeFromContext(expression: PyExpression, context: TypeEvalContext): PyType? {
        // Use the same logic as WrapWithExpectedTypeIntention for consistency
        val parent = expression.parent

        // Check if we're in an assignment statement
        if (parent is PyAssignmentStatement) {
            val targets = parent.targets
            for (target in targets) {
                if (target is PyTargetExpression) {
                    val annotationValue = target.annotation?.value
                    if (annotationValue is PyExpression) {
                        return context.getType(annotationValue)
                    }
                }
            }
        }

        // Check if we're in a function call argument
        val argumentList = PsiTreeUtil.getParentOfType(expression, com.jetbrains.python.psi.PyArgumentList::class.java)
        if (argumentList != null) {
            val callExpression =
                PsiTreeUtil.getParentOfType(argumentList, com.jetbrains.python.psi.PyCallExpression::class.java)
            if (callExpression != null) {
                // For function arguments, we'd typically use the function signature
                val callee = callExpression.callee
                if (callee is PyExpression) {
                    val calleeType = context.getType(callee)
                    return calleeType // Simplified - in real implementation, get parameter type
                }
            }
        }

        return null
    }

    /**
     * Extracts context information about where the type mismatch occurs.
     */
    private fun extractContextInfo(element: PyExpression): String {
        // Look for variable names, function calls, etc.
        var current = element.parent
        while (current != null) {
            if (current is PyAssignmentStatement) {
                val targets = current.targets
                if (targets.isNotEmpty() && targets[0] is PyTargetExpression) {
                    val varName = (targets[0] as PyTargetExpression).name
                    if (varName != null) {
                        return "variable '$varName'"
                    }
                }
            }
            current = current.parent
        }

        return ""
    }

    /**
     * Checks if child is a descendant of parent element.
     */
    private fun isChildOf(child: com.intellij.psi.PsiElement, parent: com.intellij.psi.PsiElement): Boolean {
        var current: com.intellij.psi.PsiElement? = child
        while (current != null) {
            if (current == parent) return true
            current = current.parent
        }
        return false
    }

    /**
     * Finds the PSI element at the current caret position.
     */
    private fun findElementAtCaret(editor: Editor, file: PsiFile): com.intellij.psi.PsiElement? {
        val offset = editor.caretModel.offset
        var element = file.findElementAt(offset)

        // Walk up to find a meaningful expression
        while (element != null && element !is PyExpression) {
            element = element.parent
        }

        return element
    }

    private fun isFromSupportedInspection(highlighter: RangeHighlighter): Boolean {
        val tooltip = highlighter.errorStripeTooltip ?: return false

        if (tooltip is HighlightInfo && tooltip.inspectionToolId in supportedInspectionIds) {
            return true
        }
        return false
    }

    private fun isCaretInside(h: RangeHighlighter, offset: Int): Boolean {
        val range = TextRange(h.startOffset, h.endOffset)
        return range.containsOffset(offset)
    }

    private fun isTypeMismatchMessage(textLower: String): Boolean {
        // Heuristics to detect typical "expected vs actual" messages from various languages/plugins
        val hasExpected = "expected" in textLower && "type" in textLower
        val hasActual = "actual" in textLower || "got" in textLower || "found" in textLower
        return hasExpected && hasActual
    }

    companion object {
        private const val NOTIFICATION_GROUP_ID = "TypeMismatchNotifications"
    }
}
