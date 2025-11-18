package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
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
        /**
         * Optional preferred base class name derived from surrounding context,
         * e.g. an assignment target or keyword argument name.
         */
        val preferredClassName: String? = null,
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
        val baseTypeName = target.preferredClassName ?: run {
            val capitalizedBuiltin = when (builtinName) {
                "int" -> "Int"
                "str" -> "Str"
                "float" -> "Float"
                "bool" -> "Bool"
                "bytes" -> "Bytes"
                else -> builtinName.replaceFirstChar { it.uppercaseChar() }
            }
            "Custom" + capitalizedBuiltin
        }

        val generator = PyElementGenerator.getInstance(project)

        // Generate the new class definition in the current module.
        val newTypeName = suggestUniqueClassName(pyFile, baseTypeName)
        val classText = "class $newTypeName($builtinName):\n    pass"
        val newClass = generator.createFromText(LanguageLevel.getLatest(), PyClass::class.java, classText)

        val anchor = pyFile.firstChild
        val inserted = pyFile.addBefore(newClass, anchor) as? PyClass ?: return

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

        startInlineRename(project, editor, inserted, pyFile)
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
                    val owner = PsiTreeUtil.getParentOfType(ref, PyAnnotationOwner::class.java, true)

                    val identifier = when (owner) {
                        is PyTargetExpression -> owner.name
                        is PyNamedParameter -> owner.name
                        is PyTypeDeclarationStatement -> {
                            // For type declarations like ``product_id: int`` inside
                            // classes (e.g. dataclass fields), the annotation owner
                            // is a PyTypeDeclarationStatement. Its children include
                            // the target expression for the field; use that name.
                            val firstTarget = PsiTreeUtil.getChildOfType(owner, PyTargetExpression::class.java)
                            firstTarget?.name
                        }

                        else -> null
                    }

                    val preferredName = identifier?.let { id -> deriveClassNameFromIdentifier(id) }

                    return Target(builtinName = name, annotationRef = ref, preferredClassName = preferredName)
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

        val preferredNameFromKeyword = PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java, false)
            ?.takeIf { it.valueExpression == expr }
            ?.keyword
            ?.let { deriveClassNameFromIdentifier(it) }

        val preferredNameFromAssignment = if (preferredNameFromKeyword == null) {
            val assignment = PsiTreeUtil.getParentOfType(expr, PyAssignmentStatement::class.java, false)
            if (assignment != null && assignment.assignedValue == expr) {
                val firstTarget = assignment.targets.firstOrNull() as? PyTargetExpression
                val id = firstTarget?.name
                id?.let { deriveClassNameFromIdentifier(it) }
            } else null
        } else null

        val preferredName = preferredNameFromKeyword ?: preferredNameFromAssignment

        return Target(builtinName = candidate, expression = expr, preferredClassName = preferredName)
    }

    /**
     * Derive a PascalCase class name from an identifier such as ``product_id``
     * or ``my_arg``. To keep changes minimal, we currently only derive a name
     * when the identifier contains an underscore so that simple names like
     * ``val`` continue to use the existing builtin-based naming.
     */
    private fun deriveClassNameFromIdentifier(identifier: String): String? {
        if (!identifier.contains('_')) return null

        val parts = identifier.split('_').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        return parts.joinToString(separator = "") { part ->
            if (part.isEmpty()) "" else {
                val head = part[0].uppercaseChar()
                val tail = part.substring(1)
                head + tail
            }
        }
    }

    /**
     * Ensure that the initial custom type name does not clash with an existing
     * top-level class in the target file. This mirrors the logic described in
     * docs/inline-rename.md.
     */
    private fun suggestUniqueClassName(file: PyFile, baseName: String): String {
        val usedNames = file.topLevelClasses.mapNotNull { it.name }.toMutableSet()

        if (!usedNames.contains(baseName)) return baseName

        var index = 1
        var candidate = "$baseName$index"
        while (usedNames.contains(candidate)) {
            index++
            candidate = "$baseName$index"
        }
        return candidate
    }

    /**
     * Position the caret on the newly created class name and invoke the
     * platform rename handler so that inline rename (with purple indicator)
     * is available immediately after the intention runs.
     */
    private fun startInlineRename(project: Project, editor: Editor, inserted: PyClass, pyFile: PyFile) {
        val nameId = inserted.nameIdentifier ?: return

        val document = editor.document
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)

        editor.caretModel.moveToOffset(nameId.textOffset)

        val dataContext: DataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.EDITOR, editor, null)
        val handler: RenameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext) ?: return

        if (handler.isAvailableOnDataContext(dataContext)) {
            handler.invoke(project, editor, pyFile, dataContext)
        }
    }

    companion object {
        private val SUPPORTED_BUILTINS = setOf("int", "str", "float", "bool", "bytes")
    }
}
