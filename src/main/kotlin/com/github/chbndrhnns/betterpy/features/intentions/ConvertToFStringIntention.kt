package com.github.chbndrhnns.betterpy.features.intentions

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStringLiteralExpression

class ConvertToFStringIntention : IntentionAction, HighPriorityAction, DumbAware {
    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Convert to f-string"

    override fun getFamilyName(): String = "Convert to f-string"

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is PyFile) return false
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableConvertToFStringIntention) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val literal = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression::class.java) ?: return false
        val prefix = parsePrefix(literal.text) ?: return false
        if (prefix.contains('f', ignoreCase = true)) return false
        if (prefix.contains('b', ignoreCase = true)) return false
        if (prefix.contains('u', ignoreCase = true)) return false
        return containsFStringIdentifier(literal.stringValue)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return
        if (!PluginSettingsState.instance().state.enableConvertToFStringIntention) return

        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val literal = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression::class.java) ?: return
        val prefix = parsePrefix(literal.text) ?: return
        if (prefix.contains('f', ignoreCase = true)) return
        if (prefix.contains('b', ignoreCase = true)) return
        if (prefix.contains('u', ignoreCase = true)) return
        if (!containsFStringIdentifier(literal.stringValue)) return

        val updatedText = "f${literal.text}"
        val generator = PyElementGenerator.getInstance(project)
        val replacement = generator.createExpressionFromText(
            LanguageLevel.forElement(literal),
            updatedText
        )

        WriteCommandAction.runWriteCommandAction(project, text, null, Runnable {
            literal.replace(replacement)
        }, file)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.DIFF
    }

    private fun parsePrefix(text: String): String? {
        val match = PREFIX_REGEX.find(text) ?: return null
        return match.groupValues[1]
    }

    private fun containsFStringIdentifier(value: String): Boolean {
        var idx = 0
        while (idx < value.length) {
            val ch = value[idx]
            if (ch == '{') {
                if (idx + 1 < value.length && value[idx + 1] == '{') {
                    idx += 2
                    continue
                }
                val end = value.indexOf('}', idx + 1)
                if (end == -1) return false
                val content = value.substring(idx + 1, end).trim()
                val name = content.takeWhile { it.isLetterOrDigit() || it == '_' }
                if (isValidIdentifier(name) && content.startsWith(name)) {
                    return true
                }
                idx = end + 1
                continue
            }
            if (ch == '}' && idx + 1 < value.length && value[idx + 1] == '}') {
                idx += 2
                continue
            }
            idx++
        }
        return false
    }

    private fun isValidIdentifier(name: String): Boolean {
        if (name.isEmpty()) return false
        val first = name[0]
        if (!(first == '_' || first.isLetter())) return false
        return name.all { it == '_' || it.isLetterOrDigit() }
    }

    private companion object {
        val PREFIX_REGEX = Regex("^([rRuUbBfF]*)(\"\"\"|'''|\"|')")
    }
}
