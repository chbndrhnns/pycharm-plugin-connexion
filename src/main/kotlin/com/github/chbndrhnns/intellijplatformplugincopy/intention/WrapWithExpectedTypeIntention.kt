package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Intention that wraps an expression causing a type mismatch with the expected type constructor.
 * Uses the Python type system APIs for robust type inference.
 */
class WrapWithExpectedTypeIntention : IntentionAction, HighPriorityAction, DumbAware {
    /** Simple indirection to display a chooser; overridden in tests. */
    interface PopupHost {
        fun <T> showChooser(
            editor: Editor,
            title: String,
            items: List<T>,
            render: (T) -> String,
            onChosen: (T) -> Unit
        )
    }

    class JbPopupHost : PopupHost {
        override fun <T> showChooser(
            editor: Editor,
            title: String,
            items: List<T>,
            render: (T) -> String,
            onChosen: (T) -> Unit
        ) {
            val builder = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                .createPopupChooserBuilder(items)
                .setTitle(title)
                .setRenderer(
                    com.intellij.ui.SimpleListCellRenderer.create<T>("") { value -> render(value) }
                )
                .setNamerForFiltering { value: T -> render(value) }
                .setItemChosenCallback { chosen -> onChosen(chosen) }
                .setMovable(true)
                .setResizable(true)
                .setRequestFocus(true)

            val popup = builder.createPopup()
            popup.showInBestPositionFor(editor)
        }
    }

    private var problematicElement: PyExpression? = null
    private var expectedTypeName: String? = null
    private var expectedTypeElement: PsiNamedElement? = null
    private var unionCandidates: List<Pair<String, PsiNamedElement?>> = emptyList()

    override fun getText(): String =
        if (unionCandidates.size >= 2) {
            "Wrap with expected union type…"
        } else {
            expectedTypeName?.let { "Wrap with $it()" } ?: "Wrap with expected type"
        }

    override fun getFamilyName(): String = "Type mismatch wrapper"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        problematicElement = null
        expectedTypeName = null
        expectedTypeElement = null
        unionCandidates = emptyList()

        // Find problematic element at caret
        val context = TypeEvalContext.codeAnalysis(project, file)
        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file)

        if (elementAtCaret != null) {
            val names = PyTypeIntentions.computeTypeNames(elementAtCaret, context)
            if (names.actual != null && names.expected != null && names.actual != names.expected) {
                // Derive constructor name robustly (handles unions/optionals across SDK variants)
                val ctor = PyTypeIntentions.expectedCtorName(elementAtCaret, context)
                // Attempt to collect union constructor candidates from PSI when available
                val annElement = names.expectedCtorElement
                val unionCtors = annElement?.let { collectUnionCtorCandidates(it, elementAtCaret) } ?: emptyList()

                if (unionCtors.size >= 2) {
                    // If already wrapped with any of the candidates, suppress
                    val wrappedWithAny = unionCtors.any { isAlreadyWrappedWith(elementAtCaret, it.first) }
                    if (wrappedWithAny) return false
                    problematicElement = elementAtCaret
                    // No single expectedTypeName – we will show a chooser
                    expectedTypeName = null
                    expectedTypeElement = null
                    unionCandidates = unionCtors
                    return true
                } else if (!ctor.isNullOrBlank()) {
                    if (isAlreadyWrappedWith(elementAtCaret, ctor)) return false
                    problematicElement = elementAtCaret
                    expectedTypeName = ctor

                    // Try to resolve the expected type element for import handling
                    if (names.expectedElement is PsiNamedElement) {
                        expectedTypeElement = names.expectedElement
                    }

                    return true
                }
            }
        }

        return false
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = problematicElement ?: return

        // Case 1: multiple equally suitable union candidates – show chooser or pick via test hook
        if (unionCandidates.size >= 2) {
            val candidates = unionCandidates
            val popupHost = WrapWithExpectedTypeIntentionHooks.popupHost ?: JbPopupHost()
            popupHost.showChooser(
                editor = editor,
                title = "Select expected type",
                items = candidates,
                render = { it.first },
                onChosen = { chosen ->
                    WriteCommandAction.runWriteCommandAction(project) {
                        applyWrapWith(project, file, element, chosen.first, chosen.second)
                    }
                }
            )
            return
        }

        val typeToWrapWith = expectedTypeName ?: return

        // Add import BEFORE modifying the element, using the original element as anchor
        addImportIfNeeded(file, element)

        val generator = PyElementGenerator.getInstance(project)

        // Normalize by ignoring syntactic parentheses using platform helper
        val unwrapped = PyPsiUtils.flattenParens(element) ?: element

        val wrappedExpression = if (typeToWrapWith == "str" && unwrapped is PyNumericLiteralExpression) {
            // Special case: when wrapping a bare numeric literal with expected str, replace it with a quoted literal
            generator.createExpressionFromText(
                LanguageLevel.getLatest(),
                "\"${unwrapped.text}\""
            )
        } else if (typeToWrapWith == "list") {
            // Prefer [] over list() for non-container expressions. If we're wrapping another
            // container (e.g., tuple, set, dict, list, or a comprehension/generator), keep list().
            val textToWrap = unwrapped.text
            if (isContainerExpression(unwrapped)) {
                // Use the original element text for containers to preserve required parentheses/brackets
                val containerText = problematicElement?.text ?: textToWrap
                generator.createExpressionFromText(
                    LanguageLevel.getLatest(),
                    "list($containerText)"
                )
            } else {
                generator.createExpressionFromText(
                    LanguageLevel.getLatest(),
                    "[${textToWrap}]"
                )
            }
        } else {
            val textToWrap = unwrapped.text
            generator.createExpressionFromText(
                LanguageLevel.getLatest(),
                "$typeToWrapWith($textToWrap)"
            )
        }

        element.replace(wrappedExpression)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val element = problematicElement ?: return IntentionPreviewInfo.EMPTY
        val typeToWrapWith = expectedTypeName ?: return IntentionPreviewInfo.EMPTY

        val unwrapped = PyPsiUtils.flattenParens(element) ?: element
        val originalText = unwrapped.text
        val modifiedText = when {
            typeToWrapWith == "str" && unwrapped is PyNumericLiteralExpression -> "\"$originalText\""
            typeToWrapWith == "list" -> if (isContainerExpression(unwrapped)) "list($originalText)" else "[$originalText]"
            else -> "$typeToWrapWith($originalText)"
        }

        return IntentionPreviewInfo.CustomDiff(
            file.fileType,
            file.name,
            element.text,
            modifiedText
        )
    }


    /**
     * Determines whether the given expression is a container literal or a
     * comprehension/generator, in which case wrapping with list(expr) is preferred
     * over [expr] to preserve iteration semantics rather than nesting.
     */
    private fun isContainerExpression(expr: PyExpression): Boolean {
        return when (expr) {
            is PyListLiteralExpression,
            is PyTupleExpression,
            is PySetLiteralExpression,
            is PyDictLiteralExpression,
            is PyListCompExpression,
            is PySetCompExpression,
            is PyDictCompExpression,
            is PyGeneratorExpression -> true

            else -> false
        }
    }

    override fun startInWriteAction(): Boolean = true

    /**
     * Adds import for the expected type if it's not a builtin and needs importing.
     * Prevents duplicate imports (handles both absolute and relative module forms).
     */
    private fun addImportIfNeeded(file: PsiFile, anchor: PyExpression) {
        val typeElement = expectedTypeElement ?: return

        // Don't import builtins
        if (PyBuiltinCache.getInstance(anchor).isBuiltin(typeElement)) {
            return
        }

        val typeName = typeElement.name ?: return

        val pyFile = file as? PyFile

        // If the symbol is already available in the current scope (module/locals/imports/builtins), do not import
        val owner = ScopeUtil.getScopeOwner(anchor) ?: pyFile
        if (owner != null) {
            val tec = TypeEvalContext.codeAnalysis(file.project, file)
            val resolved = PyResolveUtil.resolveQualifiedNameInScope(
                QualifiedName.fromDottedString(typeName), owner, tec
            )
            if (resolved.isNotEmpty()) {
                return
            }
        }

        // Check if already imported using any import style (absolute and relative)
        if (isImported(file, typeName)) {
            return
        }

        // Use the platform's import helper to add the import
        AddImportHelper.addImport(typeElement, file, anchor)
    }

    /**
     * Checks if the given type name is already imported in whatever import form.
     * Handles both absolute and relative imports of the symbol.
     */
    private fun isImported(file: PsiFile, name: String): Boolean {
        val pyFile = file as? PyFile ?: return false
        // Check 'from ... import ...' (absolute and relative)
        for (import in pyFile.importBlock) {
            when (import) {
                is PyFromImportStatement -> {
                    if (import.importElements.any { it.importedQName?.lastComponent == name }) {
                        return true
                    }
                }

                is PyImportStatement -> {
                    if (import.importElements.any { it.visibleName == name }) {
                        return true
                    }
                }
            }
        }
        return false
    }


    /**
     * Returns true if the given expression is already wrapped by a call to the same constructor
     * that we intend to suggest. Prevents multi-wrap like One(One("abc")).
     */
    private fun isAlreadyWrappedWith(expr: PyExpression, ctorName: String): Boolean {
        // Case A: the expression itself is a call to ctorName
        if (expr is PyCallExpression) {
            val calleeName = (expr.callee as? PyReferenceExpression)?.name
            if (calleeName == ctorName) return true
        }

        // Case B: the expression is an argument inside a call to ctorName
        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java)
        val call = PsiTreeUtil.getParentOfType(expr, PyCallExpression::class.java)
        if (argList != null && call != null && PsiTreeUtil.isAncestor(argList, expr, false)) {
            val calleeName = (call.callee as? PyReferenceExpression)?.name
            if (calleeName == ctorName) return true
        }

        return false
    }

    /** Collect simple constructor candidates from a PEP 604 union annotation PSI. */
    private fun collectUnionCtorCandidates(
        annotationExpr: PyTypedElement,
        anchorForBuiltins: PyExpression
    ): List<Pair<String, PsiNamedElement?>> {
        val result = mutableListOf<Pair<String, PsiNamedElement?>>()
        fun visit(e: PyExpression) {
            when (e) {
                is PyBinaryExpression -> {
                    if (e.operator == PyTokenTypes.OR) {
                        (e.leftExpression as? PyExpression)?.let { visit(it) }
                        (e.rightExpression as? PyExpression)?.let { visit(it) }
                        return
                    }
                }
            }
            val ref = (e as? PyReferenceExpression)
            val name = ref?.name
            if (!name.isNullOrBlank()) {
                val resolved = ref.reference.resolve() as? PsiNamedElement
                result.add(name to resolved)
            }
        }

        val expr = annotationExpr as? PyExpression ?: return emptyList()
        visit(expr)
        // Keep only distinct by name
        val distinct = result.distinctBy { it.first }
        // Consider ambiguous only if ALL candidates are non-builtin and resolved
        val builtins = PyBuiltinCache.getInstance(anchorForBuiltins)
        val nonBuiltin = distinct.filter { it.second != null && !builtins.isBuiltin(it.second!!) }
        return if (nonBuiltin.size == distinct.size && nonBuiltin.size >= 2) nonBuiltin else emptyList()
    }

    /** Apply wrapping with a given constructor name and optionally import its resolved element. */
    private fun applyWrapWith(
        project: Project,
        file: PsiFile,
        element: PyExpression,
        ctorName: String,
        resolved: PsiNamedElement?
    ) {
        // Temporarily override expectedTypeName/Element to reuse existing code paths for preview/imports
        val prevName = expectedTypeName
        val prevElem = expectedTypeElement
        try {
            expectedTypeName = ctorName
            expectedTypeElement = resolved
            // Add import first
            addImportIfNeeded(file, element)

            val generator = PyElementGenerator.getInstance(project)
            val unwrapped = PyPsiUtils.flattenParens(element) ?: element
            val textToWrap = unwrapped.text
            val wrapped = generator.createExpressionFromText(LanguageLevel.getLatest(), "$ctorName($textToWrap)")
            element.replace(wrapped)
        } finally {
            expectedTypeName = prevName
            expectedTypeElement = prevElem
        }
    }


}