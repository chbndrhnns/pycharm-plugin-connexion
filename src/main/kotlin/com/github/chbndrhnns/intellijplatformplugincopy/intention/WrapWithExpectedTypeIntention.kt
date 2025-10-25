package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.QualifiedName
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

    private var problematicElement: PyExpression? = null
    private var expectedTypeName: String? = null
    private var expectedTypeElement: PsiNamedElement? = null

    override fun getText(): String =
        expectedTypeName?.let { "Wrap with $it()" } ?: "Wrap with expected type"

    override fun getFamilyName(): String = "Type mismatch wrapper"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        problematicElement = null
        expectedTypeName = null
        expectedTypeElement = null

        // Find problematic element at caret
        val context = TypeEvalContext.codeAnalysis(project, file)
        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file)

        if (elementAtCaret != null) {
            val names = PyTypeIntentions.computeTypeNames(elementAtCaret, context)
            if (names.actual != null && names.expected != null && names.actual != names.expected) {
                problematicElement = elementAtCaret
                expectedTypeName = PyTypeIntentions.canonicalCtorName(names.expected)

                // Try to resolve the expected type element for import handling
                if (names.expectedElement is PsiNamedElement) {
                    expectedTypeElement = names.expectedElement
                }

                return true
            }
        }

        return false
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = problematicElement ?: return
        val typeToWrapWith = expectedTypeName ?: "str"

        // Add import BEFORE modifying the element, using the original element as anchor
        addImportIfNeeded(file, element)

        val generator = PyElementGenerator.getInstance(project)

        // Normalize by ignoring syntactic parentheses using platform helper
        val unwrapped = PyPsiUtils.flattenParens(element) ?: element
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

        val unwrapped = PyPsiUtils.flattenParens(element) ?: element
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


}