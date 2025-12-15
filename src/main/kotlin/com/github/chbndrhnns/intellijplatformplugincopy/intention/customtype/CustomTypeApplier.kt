package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import UsageRewriter
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.util.SlowOperations
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil

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

        // --- PHASE 1: Analysis & Search (Read / BGT) ---

        // Identify if we are acting on a parameter and need to update call sites
        val paramToUpdate: Pair<PyFunction, PyNamedParameter>? = if (plan.annotationRef != null) {
            val annRef = plan.annotationRef
            val parameter = PsiTreeUtil.getParentOfType(annRef, PyNamedParameter::class.java)
            if (parameter != null && parameter.name != null) {
                val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java)
                if (function != null) function to parameter else null
            } else null
        } else null

        val usagesToWrap: List<SmartPsiElementPointer<PyExpression>> = if (paramToUpdate != null) {
            val (function, parameter) = paramToUpdate

            // If on EDT (and not in preview/test), we must move to BGT for search
            if (!isPreview && ApplicationManager.getApplication().isDispatchThread) {
                runWithModalProgressBlocking(project, "Finding usages to update...") {
                    readAction {
                        rewriter.findUsagesToWrap(function, parameter)
                    }
                }
            } else {
                // Already in BGT or allowed context (preview)
                rewriter.findUsagesToWrap(function, parameter)
            }
        } else emptyList()

        // --- PHASE 2: Execution (Write Action) ---

        val executionBlock = {
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
                val anchorElement = plan.expression ?: plan.annotationRef
                if (anchorElement != null) {
                    imports.ensureImportedIfNeeded(pyFile, anchorElement, newClass)
                }
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

                // Check if this is a pytest.mark.parametrize parameter and wrap decorator list items
                val parameter = PsiTreeUtil.getParentOfType(annRef, PyNamedParameter::class.java)
                if (parameter != null) {
                    wrapPytestParametrizeDecoratorValues(parameter, newTypeName, pyGenerator)
                }

                // Wrap call site usages (using found pointers from Phase 1)
                if (usagesToWrap.isNotEmpty()) {
                    rewriter.wrapUsages(usagesToWrap, newTypeName, pyGenerator)
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

                // Check if this expression is a parameter reference in a pytest.mark.parametrize test
                val paramRef = originalExpr as? PyReferenceExpression
                val parameter = paramRef?.reference?.resolve() as? PyNamedParameter
                val wrappedDecorator = if (parameter != null) {
                    wrapPytestParametrizeDecoratorValues(parameter, newTypeName, pyGenerator)
                } else {
                    false
                }

                // Only wrap the expression itself if we didn't wrap decorator values
                if (!wrappedDecorator) {
                    rewriter.wrapExpression(originalExpr, newTypeName, pyGenerator)
                }
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

        if (isPreview) {
            // IntentionPreviewUtils.write handles the "write action" simulation for the preview copy
            // on the background thread without requiring EDT or causing deadlocks.
            IntentionPreviewUtils.write<RuntimeException> { executionBlock() }
        } else {
            WriteCommandAction.runWriteCommandAction(project, "Introduce Custom Type", null, executionBlock, pyFile)
        }
    }

    /**
     * Wraps values in pytest.mark.parametrize decorator lists when introducing
     * a custom type for a test parameter.
     *
     * For example, when introducing a custom type for parameter "arg" in:
     *   @pytest.mark.parametrize("arg", [1, 2, 3])
     *   def test_(arg): ...
     *
     * This will wrap the list items to produce:
     *   @pytest.mark.parametrize("arg", [Arg(1), Arg(2), Arg(3)])
     * 
     * @return true if decorator values were wrapped, false otherwise
     */
    private fun wrapPytestParametrizeDecoratorValues(
        parameter: PyNamedParameter,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ): Boolean {
        val paramName = parameter.name ?: return false
        val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return false
        val decoratorList = function.decoratorList ?: return false

        for (decorator in decoratorList.decorators) {
            // Check if this is a pytest.mark.parametrize decorator
            val decoratorName = decorator.name
            if (decoratorName != "parametrize") {
                continue
            }

            val args = decorator.argumentList?.arguments ?: continue
            if (args.isEmpty()) continue

            // First argument should be the parameter name(s)
            val namesArg = args[0]
            val paramNames = extractParameterNames(namesArg) ?: continue

            // Check if our parameter is in the list
            if (paramName !in paramNames) continue

            // Second argument should be the values list
            if (args.size < 2) continue
            val valuesArg = args[1]

            // Find the index of our parameter in the names list
            val paramIndex = paramNames.indexOf(paramName)

            // Wrap the values
            when (valuesArg) {
                is PyListLiteralExpression -> {
                    wrapListItems(valuesArg, paramIndex, paramNames.size, wrapperTypeName, generator)
                    return true
                }

                is PyTupleExpression -> {
                    wrapTupleItems(valuesArg, paramIndex, paramNames.size, wrapperTypeName, generator)
                    return true
                }
            }
        }
        return false
    }

    private fun extractParameterNames(namesArg: PyExpression): List<String>? {
        return when (namesArg) {
            is PyStringLiteralExpression -> {
                // Single string like "arg" or comma-separated like "arg1,arg2"
                namesArg.stringValue.split(',').map { it.trim() }
            }

            is PyListLiteralExpression -> {
                // List of strings like ["arg1", "arg2"]
                namesArg.elements.mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }
            }

            is PyTupleExpression -> {
                // Tuple of strings like ("arg1", "arg2")
                namesArg.elements.mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }
            }

            else -> null
        }
    }

    private fun wrapListItems(
        listExpr: PyListLiteralExpression,
        paramIndex: Int,
        paramCount: Int,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ) {
        for (element in listExpr.elements) {
            val valueToWrap = extractValueAtIndex(element, paramIndex, paramCount) ?: continue
            wrapSingleValue(valueToWrap, wrapperTypeName, generator)
        }
    }

    private fun wrapTupleItems(
        tupleExpr: PyTupleExpression,
        paramIndex: Int,
        paramCount: Int,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ) {
        for (element in tupleExpr.elements) {
            val valueToWrap = extractValueAtIndex(element, paramIndex, paramCount) ?: continue
            wrapSingleValue(valueToWrap, wrapperTypeName, generator)
        }
    }

    private fun extractValueAtIndex(element: PyExpression, index: Int, totalParams: Int): PyExpression? {
        // If there's only one parameter, the element itself is the value
        if (totalParams == 1) {
            return element
        }

        // If there are multiple parameters, each element should be a tuple/list
        return when (element) {
            is PyTupleExpression -> element.elements.getOrNull(index)
            is PyListLiteralExpression -> element.elements.getOrNull(index)
            else -> null
        }
    }

    private fun wrapSingleValue(
        expr: PyExpression,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ) {
        // Don't wrap if already wrapped
        if (expr is PyCallExpression && expr.callee?.text == wrapperTypeName) {
            return
        }

        val wrapped = generator.createExpressionFromText(
            LanguageLevel.getLatest(),
            "$wrapperTypeName(${expr.text})"
        )
        PyReplaceExpressionUtil.replaceExpression(expr, wrapped)
    }

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
        return PyTypeChecker.match(builtinType, exprType, context)
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
        val insertedPtr = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(inserted)

        ApplicationManager.getApplication().invokeLater {
            val element = insertedPtr.element ?: return@invokeLater

            // Using a specific string helps track why this slow operation is allowed.
            SlowOperations.knownIssue("Plugin: RenameDialog legacy constructor access").use {
                val dialog = RenameDialog(project, element, null, editor)
                dialog.show()
            }
        }
    }
}
