package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Applies a [CustomTypePlan] by performing all PSI mutations needed to
 * introduce a new custom type and update usages.
 *
 * Behaviour mirrors the logic that previously lived inside
 * IntroduceCustomTypeFromStdlibIntention.invoke so that existing tests keep
 * passing.
 */
class CustomTypeApplier(
    private val naming: NameSuggester = NameSuggester(),
    private val insertionPointFinder: InsertionPointFinder = InsertionPointFinder(),
    private val generator: CustomTypeGenerator = CustomTypeGenerator(),
    private val rewriter: UsageRewriter = UsageRewriter(),
    private val imports: ImportManager = ImportManager(),
) {

    fun apply(project: Project, editor: Editor, plan: CustomTypePlan, isPreview: Boolean = false) {
        val pyFile = plan.sourceFile
        val builtinName = plan.builtinName

        // If the builtin comes from a subscripted container annotation like
        // ``dict[str, list[int]]``, carry over the full annotation text –
        // including its generic arguments – into the base part of the
        // generated class. This keeps container type parameters intact on the
        // new custom container class instead of downgrading it to a plain
        // ``dict`` / ``list`` / ``set``.
        val builtinForClass = generator.determineBaseClassText(builtinName, plan.annotationRef)

        val baseTypeName = naming.suggestTypeName(builtinName, plan.preferredClassName)

        val pyGenerator = PyElementGenerator.getInstance(project)

        // Decide which file should host the newly introduced custom type.
        val targetFileForNewClass = insertionPointFinder.chooseFile(plan.field, plan.expression, pyFile)

        // Generate the new class definition in the chosen module.
        val newTypeName = naming.ensureUnique(targetFileForNewClass, baseTypeName)
        val newClass = generator.insertClass(
            targetFileForNewClass,
            generator.createClass(project, newTypeName, builtinForClass),
        )

        // When the custom type is introduced in a different module than the
        // current file (e.g. dataclass declaration vs usage site), make sure
        // the usage file imports the new type so that wrapped calls resolve
        // correctly and no circular import is introduced.
        if (targetFileForNewClass != pyFile) {
            val anchorElement = plan.expression ?: plan.annotationRef ?: return
            imports.ensureImportedIfNeeded(pyFile, anchorElement, newClass)
        }

        // Replace the annotation reference or expression usage with the new type.
        val newRefBase = pyGenerator.createExpressionFromText(LanguageLevel.getLatest(), newTypeName)

        if (plan.annotationRef != null) {
            val annRef = plan.annotationRef

            val assignedExpr = plan.assignedExpression
            if (assignedExpr != null) {
                // For annotated assignments (where we also have a right-hand
                // side expression), we mirror the behaviour of simple
                // annotated assignments: the declared type on the variable
                // is updated to the custom type. We use rewriteAnnotation
                // to handle both simple types, containers (stripping args),
                // and unions (preserving other branches).
                rewriter.rewriteAnnotation(annRef, newRefBase, builtinName)
                // Only wrap the right-hand side when its inferred type
                // corresponds to the builtin at the caret (the branch we are
                // replacing). For union annotations like ``int | None`` with
                // a default of ``None``, the RHS has type ``None`` so it will
                // not be wrapped.
                if (shouldWrapAssignedExpression(assignedExpr, builtinName)) {
                    rewriter.wrapExpression(assignedExpr, newTypeName, pyGenerator)
                }
            } else {
                // Non-assignment annotations (parameters, returns, dataclass
                // fields, etc.) keep their existing container type arguments
                // and only swap out the builtin name.
                rewriter.rewriteAnnotation(annRef, newRefBase, builtinName)
            }
        }

        if (plan.expression != null) {
            val originalExpr = plan.expression

            // When the intention is invoked from a call-site expression,
            // also update the corresponding parameter annotation in the
            // same scope (if any) so that the declared type matches the
            // newly introduced custom type. This must happen *before*
            // we rewrite the argument expression so that the PSI
            // hierarchy for the original expression is still intact.
            rewriter.updateParameterAnnotationFromCallSite(originalExpr, builtinName, newTypeName, pyGenerator)
            rewriter.wrapExpression(originalExpr, newTypeName, pyGenerator)
        }

        val field = plan.field
        if (field != null) {
            // If we introduced the type from a call-site expression, there was
            // no annotationRef to rewrite above. In that case, synchronise the
            // dataclass field annotation now.
            if (plan.annotationRef == null) {
                rewriter.syncDataclassFieldAnnotation(field, builtinName, newRefBase)
            }

            // Wrap usages in the *current* file where we invoked the
            // intention. Cross-module wrapping is intentionally left to future
            // enhancements; for now we only guarantee intra-file wrapping,
            // while the custom type itself lives with the dataclass
            // declaration.
            rewriter.wrapDataclassConstructorUsages(pyFile, field, newTypeName, pyGenerator)

            // Project-wide update: find all references to the dataclass across
            // the project and wrap constructor arguments at those call sites.
            if (!isPreview) {
                wrapDataclassConstructorUsagesProjectWide(project, field, newTypeName, newClass, pyGenerator)
            }
        }

        // Only trigger inline rename when the new class was inserted into the
        // same file that the user is currently editing *and* we did not
        // already derive a semantic preferred class name from context. For
        // names like ``ProductId`` that come from identifiers such as
        // ``product_id``, we want to keep that name stable instead of letting
        // the rename infrastructure adjust it (which may, for example,
        // normalise it to ``Productid``). Inline rename remains available for
        // generic names like ``CustomInt``.
        if (!isPreview && targetFileForNewClass == pyFile && plan.preferredClassName == null) {
            startInlineRename(project, editor, newClass, pyFile)
        }
    }

    /**
     * Decide whether an assigned expression should be wrapped when updating an
     * annotated assignment.
     *
     * The goal is to only wrap values whose *inferred type* clearly matches the
     * builtin type we are replacing, and to avoid wrapping sentinel/literal
     * values that belong to other union branches (most importantly ``None``).
     */
    private fun shouldWrapAssignedExpression(expr: PyExpression, builtinName: String): Boolean {
        if (expr is PyNoneLiteralExpression) return false

        val project = expr.project
        val context = TypeEvalContext.codeInsightFallback(project)
        val exprType = context.getType(expr)


        val builtinCache = PyBuiltinCache.getInstance(expr)
        val builtinType: PyType? = when (builtinName) {
            "int" -> builtinCache.intType
            "float" -> builtinCache.floatType
            "str" -> builtinCache.strType
            "bool" -> builtinCache.boolType
            else -> null
        }

        // We treat the RHS as belonging to the builtin branch only when its
        // inferred type is compatible with the builtin type we are replacing.
        if (PyTypeChecker.match(builtinType, exprType, context)) {
            return true
        }
        return false
    }

    private fun wrapDataclassConstructorUsagesProjectWide(
        project: Project,
        field: PyTargetExpression,
        wrapperTypeName: String,
        introducedClass: PyClass,
        generator: PyElementGenerator,
    ) {
        val pyClass = PsiTreeUtil.getParentOfType(field, PyClass::class.java) ?: return
        val searchScope = GlobalSearchScope.projectScope(project)
        val refs = ReferencesSearch.search(pyClass, searchScope).findAll()

        // Process each distinct Python file that contains a reference to the class.
        refs.mapNotNull { it.element.containingFile as? PyFile }
            .distinct()
            .forEach { refFile ->
                // Update all constructor usages in that file.
                rewriter.wrapDataclassConstructorUsages(refFile, field, wrapperTypeName, generator)

                // Ensure the new type is imported where needed.
                val anchor = PsiTreeUtil.findChildOfType(refFile, PyTypedElement::class.java)
                    ?: refFile.firstChild as? PyTypedElement
                if (anchor != null) {
                    imports.ensureImportedIfNeeded(refFile, anchor, introducedClass)
                }
            }
    }

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
}
