package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext
import javax.swing.Icon

/**
 * Intention that introduces a simple custom domain type from a stdlib/builtin
 * type used in an annotation. For the first iteration we keep the behavior
 * deliberately small and test-driven:
 *
 * - Only reacts to builtin names like ``int`` when used in annotations.
 * - Generates a subclass ``CustomInt(int): pass`` in the current module.
 * - Rewrites the current annotation to refer to the new type.
 */
class IntroduceCustomTypeFromStdlibIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {

    private var lastText: String = "Introduce custom type from stdlib type"

    private data class Target(
        val builtinName: String,
        val annotationRef: PyReferenceExpression? = null,
        val expression: PyExpression? = null,
    )

    override fun getText(): String = lastText

    override fun getFamilyName(): String = "Introduce custom type from stdlib type"

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val pyFile = file as? PyFile ?: return false

        val target = findTarget(editor, pyFile) ?: run {
            lastText = "Introduce custom type from stdlib type"
            return false
        }

        lastText = "Introduce custom type from ${target.builtinName}"
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val pyFile = file as? PyFile ?: return
        val target = findTarget(editor, pyFile) ?: return

        val builtinName = target.builtinName
        val newTypeName = "Custom" + builtinName.replaceFirstChar { it.uppercaseChar() }

        val generator = PyElementGenerator.getInstance(project)

        // Generate the new class definition in the current module.
        val classText = "class $newTypeName($builtinName):\n    pass"
        val newClass = generator.createFromText(LanguageLevel.getLatest(), PyClass::class.java, classText)

        val anchor = pyFile.firstChild
        pyFile.addBefore(newClass, anchor)

        // Replace the annotation reference or expression usage with the new type.
        val newRef = generator.createExpressionFromText(LanguageLevel.getLatest(), newTypeName)
        when {
            target.annotationRef != null -> {
                target.annotationRef.replace(newRef)
            }

            target.expression != null -> {
                val exprText = target.expression.text
                val wrapped = generator.createExpressionFromText(
                    LanguageLevel.getLatest(),
                    "$newTypeName($exprText)",
                )
                target.expression.replace(wrapped)
            }
        }
    }

    override fun startInWriteAction(): Boolean = true

    private fun findTarget(editor: Editor, file: PyFile): Target? {
        val offset = editor.caretModel.offset
        val leaf = file.findElementAt(offset) ?: return null

        // 1) Prefer the original behavior: caret on a builtin name inside an annotation.
        val ref = PsiTreeUtil.getParentOfType(leaf, PyReferenceExpression::class.java, false)
        if (ref != null) {
            val name = ref.name
            if (name != null && name in SUPPORTED_BUILTINS) {
                val annotation = PsiTreeUtil.getParentOfType(ref, PyAnnotation::class.java, false)
                if (annotation != null) {
                    return Target(builtinName = name, annotationRef = ref)
                }
            }
        }

        // 2) New behavior: caret on an expression whose (actual or expected) type is
        // a supported builtin. This covers cases like arguments and literals where
        // the stdlib type is implied rather than written explicitly.
        val exprFromCaret = PyTypeIntentions.findExpressionAtCaret(editor, file)
        val expr = exprFromCaret as? PyExpression
            ?: PsiTreeUtil.getParentOfType(leaf, PyStringLiteralExpression::class.java, false)
            ?: PsiTreeUtil.getParentOfType(leaf, PyNumericLiteralExpression::class.java, false)
            ?: return null

        val ctx = TypeEvalContext.codeAnalysis(file.project, file)
        val typeNames = PyTypeIntentions.computeDisplayTypeNames(expr, ctx)
        var candidate: String? = typeNames.expected ?: typeNames.actual

        // When type inference doesn't give us a usable builtin name (e.g. bare
        // literals with no annotation, or literal-specific names like
        // ``Literal['str']``), fall back to simple literal kinds.
        if (candidate == null || candidate !in SUPPORTED_BUILTINS) {
            candidate = when (expr) {
                is PyStringLiteralExpression -> "str"
                is PyNumericLiteralExpression ->
                    // Respect float vs int literals so that e.g. `4567.6` is
                    // treated as ``float`` rather than ``int``.
                    if (expr.isIntegerLiteral) "int" else "float"

                else -> candidate
            }
        }

        candidate ?: return null
        if (candidate !in SUPPORTED_BUILTINS) return null

        return Target(builtinName = candidate, expression = expr)
    }

    companion object {
        private val SUPPORTED_BUILTINS = setOf("int", "str", "float", "bool", "bytes")
    }
}
