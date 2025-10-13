package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel

/**
 * Offers a quick-fix intention when the caret is on a highlight that reports
 * an "expected type vs actual type" mismatch. Triggering the action shows a
 * balloon notification with the full highlight description.
 *
 * Note: This implementation uses the public MarkupModel/RangeHighlighter API as
 * recommended in docs/highlighter.md and avoids accessing private impl classes
 * like HighlightInfo/DaemonCodeAnalyzerImpl#getHighlights.
 */
class TypeMismatchQuickFixIntention : IntentionAction, HighPriorityAction, DumbAware {

    private var lastMessage: String? = null

    override fun getText(): String = "Show type mismatch details"

    override fun getFamilyName(): String = "Type mismatch helper"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        lastMessage = null
        val message = findTypeMismatchMessageAtCaret(project, editor)
        if (!message.isNullOrBlank()) {
            lastMessage = message
        }
        return lastMessage != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val message = lastMessage ?: return
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                "Type mismatch",
                message,
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    override fun startInWriteAction(): Boolean = false

    private fun findTypeMismatchMessageAtCaret(project: Project, editor: Editor): String? {
        val offset = editor.caretModel.offset
        val document = editor.document

        val mm = (DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx) ?: return null

        var best: RangeHighlighter? = null

        // Process very small range around caret to keep it efficient
        val start = maxOf(0, offset - 1)
        val end = minOf(document.textLength, offset + 1)

        mm.processRangeHighlightersOverlappingWith(start, end) { h ->
            if (h.isValid && h.targetArea == HighlighterTargetArea.EXACT_RANGE && h.errorStripeTooltip != null) {
                if (isCaretInside(h, offset)) {
                    best = chooseBetter(best, h)
                }
            }
            true
        }

        val tooltipText = best?.errorStripeTooltip?.let { tipObj ->
            when (tipObj) {
                is String -> tipObj
                else -> tipObj.toString()
            }
        } ?: return null

        val normalized = tooltipText.trim().lowercase()
        return if (isTypeMismatchMessage(normalized)) tooltipText else null
    }

    private fun chooseBetter(current: RangeHighlighter?, candidate: RangeHighlighter): RangeHighlighter {
        if (current == null) return candidate
        val currLayer = current.layer
        val candLayer = candidate.layer
        return when {
            candLayer != currLayer -> if (candLayer > currLayer) candidate else current
            else -> {
                val currLen = (current.endOffset - current.startOffset)
                val candLen = (candidate.endOffset - candidate.startOffset)
                if (candLen < currLen) candidate else current
            }
        }
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
