package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
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
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeywordArgument
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

        // First check if we have a type mismatch highlight at or near the caret
        if (!hasTypeMismatchHighlightAtCaret(project, editor, file)) {
            return false
        }

        // Try to compute type mismatch details using Python type system
        val element = PyTypeIntentions.findExpressionAtCaret(editor, file)
        if (element != null) {
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
     * Checks if there's a type mismatch highlight at or near the caret.
     * If the caret expression is within a PyKeywordArgument, we test overlap against the
     * entire keyword argument range to better match how inspections attach highlights.
     */
    private fun hasTypeMismatchHighlightAtCaret(project: Project, editor: Editor, file: PsiFile): Boolean {
        val offset = editor.caretModel.offset
        val document = editor.document

        val mm = (DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx) ?: return false

        // Try to find a nearby keyword argument to broaden the search range
        val expr = PyTypeIntentions.findExpressionAtCaret(editor, file)
        val kwArg = expr?.let { PsiTreeUtil.getParentOfType(it, PyKeywordArgument::class.java) }

        val rangeToCheck: TextRange = if (kwArg != null) {
            kwArg.textRange
        } else {
            // Slightly broader caret window to be more forgiving
            TextRange(maxOf(0, offset - 20), minOf(document.textLength, offset + 20))
        }

        var found = false
        mm.processRangeHighlightersOverlappingWith(rangeToCheck.startOffset, rangeToCheck.endOffset) { h ->
            if (h.isValid && h.targetArea == HighlighterTargetArea.EXACT_RANGE && h.errorStripeTooltip != null) {
                if (isFromSupportedInspection(h)) {
                    val tooltipText = h.errorStripeTooltip?.toString() ?: ""
                    if (isTypeMismatchMessage(tooltipText.lowercase())) {
                        // If we're not in a keyword arg, keep the stricter caret-inside check
                        if (kwArg == null && !isCaretInside(h, offset)) {
                            return@processRangeHighlightersOverlappingWith true
                        }
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
            val names = PyTypeIntentions.computeTypeNames(element, context)

            if (names.actual != null && names.expected != null && names.actual != names.expected) {
                val contextInfo = PyTypeIntentions.extractContextInfo(element)
                return TypeMismatchDetails(names.expected!!, names.actual!!, contextInfo)
            }

        } catch (_: Exception) {
        }

        return null
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
