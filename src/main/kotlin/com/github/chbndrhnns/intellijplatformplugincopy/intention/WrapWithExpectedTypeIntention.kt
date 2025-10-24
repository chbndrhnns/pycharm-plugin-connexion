package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyType
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
        val element = findExpressionAtCaret(editor, file) ?: return false

        // Use Python type system to compute expected type
        val context = TypeEvalContext.codeAnalysis(project, file)
        val actualType = context.getType(element)
        val expectedType = getExpectedTypeFromContext(element, context)

        // Only show if we have both actual and expected types and they differ
        if (actualType != null && expectedType != null && actualType != expectedType) {
            problematicElement = element
            expectedTypeName = getExpectedTypeString(element, context)
            return true
        }

        return false
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = problematicElement ?: return
        val typeToWrapWith = expectedTypeName ?: "str"

        val generator = PyElementGenerator.getInstance(project)
        val wrappedExpression = generator.createExpressionFromText(
            com.jetbrains.python.psi.LanguageLevel.getLatest(),
            "$typeToWrapWith(${element.text})"
        )

        element.replace(wrappedExpression)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val element = problematicElement ?: return IntentionPreviewInfo.EMPTY
        val typeToWrapWith = expectedTypeName ?: "str"

        // Create the original and modified text for a custom diff preview
        val originalText = element.text
        val modifiedText = "$typeToWrapWith($originalText)"

        return IntentionPreviewInfo.CustomDiff(
            file.fileType,
            file.name,
            originalText,
            modifiedText
        )
    }

    override fun startInWriteAction(): Boolean = true

    private fun getExpectedTypeFromContext(expression: PyExpression, context: TypeEvalContext): PyType? {
        // Use PyTypeProvider extension point approach - get expected type from assignment context
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
        val argumentList = PsiTreeUtil.getParentOfType(expression, PyArgumentList::class.java)
        if (argumentList != null) {
            val callExpression = PsiTreeUtil.getParentOfType(argumentList, PyCallExpression::class.java)
            if (callExpression != null) {
                // For function arguments, we'd typically use the function signature
                // This is where PyTypeProvider extension points would be most useful
                val callee = callExpression.callee
                if (callee is PyExpression) {
                    val calleeType = context.getType(callee)
                    return calleeType // Simplified - in real implementation, get parameter type
                }
            }
        }

        return null
    }

    private fun getExpectedTypeString(expression: PyExpression, context: TypeEvalContext): String {
        val expectedType = getExpectedTypeFromContext(expression, context)
        return when {
            expectedType != null -> {
                val typeName = expectedType.name ?: "Unknown"
                // Handle common type names
                when (typeName) {
                    "str" -> "str"
                    "int" -> "int"
                    "float" -> "float"
                    "bool" -> "bool"
                    "list" -> "list"
                    "dict" -> "dict"
                    else -> typeName
                }
            }

            else -> "str" // Default fallback
        }
    }

    /**
     * Finds a PyExpression at the current caret position.
     * Uses a more targeted approach to find expressions suitable for wrapping.
     */
    private fun findExpressionAtCaret(editor: Editor, file: PsiFile): PyExpression? {
        val offset = editor.caretModel.offset
        val elementAtCaret = file.findElementAt(offset) ?: return null

        // Walk up the PSI tree to find a PyExpression, preferring call expressions
        var current: PsiElement? = elementAtCaret
        var bestExpression: PyExpression? = null

        while (current != null) {
            if (current is PyExpression) {
                bestExpression = current
                // Prefer call expressions over literals for more meaningful wrapping
                if (current is com.jetbrains.python.psi.PyCallExpression) {
                    return current
                }
            }
            current = current.parent
        }

        return bestExpression
    }
}