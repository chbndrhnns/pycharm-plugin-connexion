package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.*
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
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.jetbrains.python.psi.*
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
    private val imports = ImportManager()
    private val naming = NameSuggester()
    private val planBuilder = PlanBuilder()
    private val insertionPointFinder = InsertionPointFinder()
    private val generator = CustomTypeGenerator()
    private val rewriter = UsageRewriter()

    override fun getText(): String = lastText

    override fun getFamilyName(): String = "Introduce custom type from stdlib type"

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val pyFile = file as? PyFile ?: return false

        val plan = planBuilder.build(editor, pyFile) ?: run {
            lastText = "Introduce custom type from stdlib type"
            return false
        }

        lastText = "Introduce custom type from ${plan.builtinName}"
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val pyFile = file as? PyFile ?: return
        val plan = planBuilder.build(editor, pyFile) ?: return

        val builtinName = plan.builtinName
        val baseTypeName = naming.suggestTypeName(builtinName, plan.preferredClassName)

        val pyGenerator = PyElementGenerator.getInstance(project)

        // Decide which file should host the newly introduced custom type.
        val targetFileForNewClass = insertionPointFinder.chooseFile(plan.field, plan.expression, pyFile)

        // Generate the new class definition in the chosen module.
        val newTypeName = naming.ensureUnique(targetFileForNewClass, baseTypeName)
        val newClass = generator.insertClass(
            targetFileForNewClass,
            generator.createClass(project, newTypeName, builtinName),
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
        val newRef = pyGenerator.createExpressionFromText(LanguageLevel.getLatest(), newTypeName)
        when {
            plan.annotationRef != null -> {
                rewriter.rewriteAnnotation(plan.annotationRef, newRef)
            }

            plan.expression != null -> {
                val originalExpr = plan.expression

                // When the intention is invoked from a call-site expression,
                // also update the corresponding parameter annotation in the
                // same scope (if any) so that the declared type matches the
                // newly introduced custom type. This must happen *before*
                // we rewrite the argument expression so that the PSI
                // hierarchy for the original expression is still intact.
                rewriter.updateParameterAnnotationFromCallSite(originalExpr, newTypeName, pyGenerator)
                rewriter.wrapExpression(originalExpr, newTypeName, pyGenerator)
            }
        }

        // When the builtin comes from a dataclass field (either directly from
        // its annotation or from a constructor call-site argument), make sure
        // we also align the field's annotation and wrap all constructor
        // usages so that arguments are passed as the new custom type.
        val field = plan.field
        if (field != null) {
            // If we introduced the type from a call-site expression, there was
            // no annotationRef to rewrite above. In that case, synchronise the
            // dataclass field annotation now.
            if (plan.annotationRef == null) {
                rewriter.syncDataclassFieldAnnotation(field, builtinName, newRef)
            }

            // Wrap usages in the *current* file where we invoked the
            // intention. Cross-module wrapping is intentionally left to future
            // enhancements; for now we only guarantee intra-file wrapping,
            // while the custom type itself lives with the dataclass
            // declaration.
            rewriter.wrapDataclassConstructorUsages(pyFile, field, newTypeName, pyGenerator)

            // Project-wide update: find all references to the dataclass across
            // the project and wrap constructor arguments at those call sites.
            wrapDataclassConstructorUsagesProjectWide(project, field, newTypeName, newClass, pyGenerator)
        }

        // Only trigger inline rename when the new class was inserted into the
        // same file that the user is currently editing *and* we did not
        // already derive a semantic preferred class name from context. For
        // names like ``ProductId`` that come from identifiers such as
        // ``product_id``, we want to keep that name stable instead of letting
        // the rename infrastructure adjust it (which may, for example,
        // normalise it to ``Productid``). Inline rename remains available for
        // generic names like ``CustomInt``.
        if (targetFileForNewClass == pyFile && plan.preferredClassName == null) {
            startInlineRename(project, editor, newClass, pyFile)
        }
    }

    override fun startInWriteAction(): Boolean = true

    /**
     * When the intention is started from a call-site expression, try to find
     * the corresponding parameter in the resolved callable and update its
     * annotation from the builtin (e.g. ``str``) to the newly introduced
     * custom type. This keeps annotations in the same logical scope in sync
     * with the wrapped argument.
     */

    /**
     * Project-wide wrapper: locate all files in the project that reference the
     * dataclass owning [field] and apply the same constructor usage wrapping as
     * we do for a single file. Also ensures the newly introduced type is
     * importable in those files.
     */
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
}
