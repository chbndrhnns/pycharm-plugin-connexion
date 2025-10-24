package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Intention that wraps an expression causing a type mismatch with the expected type constructor.
 * Uses the Python type system APIs for robust type inference.
 */
class WrapWithExpectedTypeIntention : IntentionAction, HighPriorityAction, DumbAware {

    private var problematicElement: PyExpression? = null
    private var expectedTypeName: String? = null

    override fun getText(): String =
        expectedTypeName?.let { "Wrap with $it()" } ?: "Wrap with expected type"

    override fun getFamilyName(): String = "Type mismatch wrapper"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        problematicElement = null
        expectedTypeName = null

        // Find problematic element at caret
        val element = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return false

        // Use Python type system to compute expected type
        val context = TypeEvalContext.codeAnalysis(project, file)
        val names = PyTypeIntentions.computeTypeNames(element, context)

        // Only show if we have both actual and expected types and they differ
        if (names.actual != null && names.expected != null && names.actual != names.expected) {
            problematicElement = element
            expectedTypeName = PyTypeIntentions.canonicalCtorName(names.expected)
            return true
        }

        return false
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = problematicElement ?: return
        val typeToWrapWith = expectedTypeName ?: "str"

        val generator = PyElementGenerator.getInstance(project)

        // Use PSI-based approach to unwrap parentheses
        val unwrapped = unwrapParen(element) ?: element
        val textToWrap = unwrapped.text

        val wrappedExpression = generator.createExpressionFromText(
            LanguageLevel.getLatest(),
            "$typeToWrapWith($textToWrap)"
        )

        element.replace(wrappedExpression)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val element = problematicElement ?: return IntentionPreviewInfo.EMPTY
        val typeToWrapWith = expectedTypeName ?: "str"

        val unwrapped = unwrapParen(element) ?: element
        val originalText = unwrapped.text
        val modifiedText = "$typeToWrapWith($originalText)"

        return IntentionPreviewInfo.CustomDiff(
            file.fileType,
            file.name,
            element.text,
            modifiedText
        )
    }

    override fun startInWriteAction(): Boolean = true

    /**
     * Unwrap parenthesized expressions.
     * Recursively unwraps nested parentheses until reaching the actual expression.
     */
    private fun unwrapParen(expr: PyExpression?): PyExpression? {
        var cur = expr
        while (cur is PyParenthesizedExpression) {
            cur = cur.containedExpression  // may become null for "()"
        }
        return cur
    }
}