package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyImportService
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
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
    private val imports = PyImportService()

    private data class Target(
        val builtinName: String,
        val annotationRef: PyReferenceExpression? = null,
        val expression: PyExpression? = null,
        /**
         * Optional preferred base class name derived from surrounding context,
         * e.g. an assignment target or keyword argument name.
         */
        val preferredClassName: String? = null,
        /**
         * When the builtin appears as a dataclass field (either via its
         * annotation or via a constructor argument at a call site), this holds
         * the corresponding dataclass field target expression. It is used to
         * locate and update all constructor usages so that their arguments are
         * wrapped in the newly introduced custom type.
         */
        val field: PyTargetExpression? = null,
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

        // Decide which file should host the newly introduced custom type. When
        // we are working with a dataclass field, we want the custom type to
        // live alongside the dataclass *declaration* (which may be in a
        // different module than the current usage site). If we are invoked on
        // a call-site expression referencing a dataclass but could not
        // resolve the specific field, still prefer the dataclass declaration
        // file. Otherwise, fall back to the current file as before.
        val sourceFileFromField = (target.field?.containingFile as? PyFile)
        val sourceFileFromCall = if (target.field == null && target.expression != null) {
            val call = PsiTreeUtil.getParentOfType(target.expression, PyCallExpression::class.java, false)
            val callee = call?.callee as? PyReferenceExpression
            val resolvedClass = callee?.reference?.resolve() as? PyClass
            if (resolvedClass != null && isDataclassClass(resolvedClass)) {
                resolvedClass.containingFile as? PyFile
            } else null
        } else null

        val targetFileForNewClass = sourceFileFromField ?: sourceFileFromCall ?: pyFile

        // Generate the new class definition in the chosen module.
        val newTypeName = suggestUniqueClassName(targetFileForNewClass, baseTypeName)
        val classText = "class $newTypeName($builtinName):\n    pass"
        val newClass = generator.createFromText(LanguageLevel.getLatest(), PyClass::class.java, classText)

        // Ensure that all import statements remain at the top of the file.
        // If the target module already has imports, we insert the new class
        // *after* the last import statement; otherwise we fall back to the
        // previous behaviour of inserting at the very beginning.
        val importAnchor: PsiElement? = targetFileForNewClass.importBlock.lastOrNull() as? PsiElement
        val inserted = if (importAnchor != null) {
            targetFileForNewClass.addAfter(newClass, importAnchor) as? PyClass
        } else {
            val firstChild = targetFileForNewClass.firstChild
            targetFileForNewClass.addBefore(newClass, firstChild) as? PyClass
        } ?: return

        // When the custom type is introduced in a different module than the
        // current file (e.g. dataclass declaration vs usage site), make sure
        // the usage file imports the new type so that wrapped calls resolve
        // correctly and no circular import is introduced.
        if (targetFileForNewClass != pyFile) {
            val anchorElement = target.expression ?: target.annotationRef ?: return
            imports.ensureImportedIfNeeded(pyFile, anchorElement, inserted)
        }

        // Replace the annotation reference or expression usage with the new type.
        val newRef = generator.createExpressionFromText(LanguageLevel.getLatest(), newTypeName)
        when {
            target.annotationRef != null -> {
                target.annotationRef.replace(newRef)
            }

            target.expression != null -> {
                val originalExpr = target.expression

                // When the intention is invoked from a call-site expression,
                // also update the corresponding parameter annotation in the
                // same scope (if any) so that the declared type matches the
                // newly introduced custom type. This must happen *before*
                // we rewrite the argument expression so that the PSI
                // hierarchy for the original expression is still intact.
                updateParameterAnnotationFromCallSite(originalExpr, newTypeName, generator)

                val exprText = originalExpr.text
                val wrapped = generator.createExpressionFromText(
                    LanguageLevel.getLatest(),
                    "$newTypeName($exprText)",
                )
                originalExpr.replace(wrapped)
            }
        }

        // When the builtin comes from a dataclass field (either directly from
        // its annotation or from a constructor call-site argument), make sure
        // we also align the field's annotation and wrap all constructor
        // usages so that arguments are passed as the new custom type.
        val field = target.field
        if (field != null) {
            // If we introduced the type from a call-site expression, there was
            // no annotationRef to rewrite above. In that case, synchronise the
            // dataclass field annotation now.
            if (target.annotationRef == null) {
                val typeDecl = PsiTreeUtil.getParentOfType(field, PyTypeDeclarationStatement::class.java, false)
                val annExpr = typeDecl?.annotation?.value
                if (annExpr != null) {
                    // Replace the builtin reference inside the annotation with
                    // the new type reference, falling back to a plain
                    // replacement when the annotation is just the builtin
                    // name.
                    val builtinRefInAnn = PsiTreeUtil.findChildOfType(annExpr, PyReferenceExpression::class.java)
                    val replacement = newRef.copy() as PyExpression
                    when {
                        builtinRefInAnn != null && builtinRefInAnn.name == builtinName ->
                            builtinRefInAnn.replace(replacement)

                        annExpr.text == builtinName ->
                            annExpr.replace(replacement)
                    }
                }
            }

            // Wrap usages in the *current* file where we invoked the
            // intention. Cross-module wrapping is intentionally left to future
            // enhancements; for now we only guarantee intra-file wrapping,
            // while the custom type itself lives with the dataclass
            // declaration.
            wrapDataclassConstructorUsages(pyFile, field, newTypeName, generator)

            // Project-wide update: find all references to the dataclass across
            // the project and wrap constructor arguments at those call sites.
            wrapDataclassConstructorUsagesProjectWide(project, field, newTypeName, inserted, generator)
        }

        // Only trigger inline rename when the new class was inserted into the
        // same file that the user is currently editing *and* we did not
        // already derive a semantic preferred class name from context. For
        // names like ``ProductId`` that come from identifiers such as
        // ``product_id``, we want to keep that name stable instead of letting
        // the rename infrastructure adjust it (which may, for example,
        // normalise it to ``Productid``). Inline rename remains available for
        // generic names like ``CustomInt``.
        if (targetFileForNewClass == pyFile && target.preferredClassName == null) {
            startInlineRename(project, editor, inserted, pyFile)
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
                    val owner = PsiTreeUtil.getParentOfType(ref, PyAnnotationOwner::class.java, true)

                    var dataclassField: PyTargetExpression? = null
                    val identifier = when (owner) {
                        is PyTargetExpression -> {
                            // Parameter / assignment-style annotation; may be a
                            // dataclass field as well.
                            dataclassField = owner.takeIf { isDataclassField(it) }
                            owner.name
                        }

                        is PyNamedParameter -> owner.name
                        is PyTypeDeclarationStatement -> {
                            // For type declarations like ``product_id: int`` inside
                            // classes (e.g. dataclass fields), the annotation owner
                            // is a PyTypeDeclarationStatement. Its children include
                            // the target expression for the field; use that name.
                            val firstTarget = PsiTreeUtil.getChildOfType(owner, PyTargetExpression::class.java)
                            dataclassField = firstTarget?.takeIf { isDataclassField(it) }
                            firstTarget?.name
                        }

                        else -> null
                    }

                    val preferredName = identifier?.let { id -> deriveClassNameFromIdentifier(id) }

                    return Target(
                        builtinName = name,
                        annotationRef = ref,
                        preferredClassName = preferredName,
                        field = dataclassField,
                    )
                }
            }
        }

        // 2) New behavior: caret on an expression whose (actual or expected) type is
        // a supported builtin. This covers cases like arguments and literals where
        // the stdlib type is implied rather than written explicitly.
        val exprFromCaret = PyTypeIntentions.findExpressionAtCaret(editor, file)
        val expr = exprFromCaret
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

        // If the caret expression is already wrapped in a non-stdlib call
        // (e.g. ProductId(123)), do not offer introducing another custom
        // type from the builtin literal.
        if (isAlreadyWrappedInCustomType(expr)) return null

        val preferredNameFromKeyword = PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java, false)
            ?.takeIf { it.valueExpression == expr }
            ?.keyword
            ?.let { deriveClassNameFromIdentifier(it) }

        val preferredNameFromAssignment = if (preferredNameFromKeyword == null) {
            val assignment = PsiTreeUtil.getParentOfType(expr, PyAssignmentStatement::class.java, false)
            if (assignment != null) {
                // Prefer deriving the class name from the assignment target
                // whenever the caret expression participates in an
                // assignment like ``product_id = 1234``. We intentionally do
                // not require that the caret expression is *exactly* the
                // assigned value expression because helper utilities may
                // return sub‑expressions; using the target name is still the
                // right behaviour in all such cases.
                val firstTarget = assignment.targets.firstOrNull() as? PyTargetExpression
                val id = firstTarget?.name
                id?.let { deriveClassNameFromIdentifier(it) }
            } else null
        } else null

        val dataclassFieldFromExpr = findDataclassFieldForExpression(expr)

        // If we are in a dataclass context but the field is already annotated
        // with a non-stdlib type (e.g. ``ProductId(int)``), we should not offer
        // to introduce yet another custom type from the builtin used at the
        // call site. This avoids suggesting "Introduce custom type from int"
        // when the declaration already uses a domain type.
        if (dataclassFieldFromExpr != null) {
            val typeDecl =
                PsiTreeUtil.getParentOfType(dataclassFieldFromExpr, PyTypeDeclarationStatement::class.java, false)
            val annExpr = typeDecl?.annotation?.value as? PyExpression
            val annRef = annExpr as? PyReferenceExpression
            val annName = annRef?.name
            if (annName != null && annName !in SUPPORTED_BUILTINS) {
                return null
            }
        }

        val preferredName = preferredNameFromKeyword
            ?: preferredNameFromAssignment
            ?: dataclassFieldFromExpr?.name?.let { deriveClassNameFromIdentifier(it) }

        return Target(
            builtinName = candidate,
            expression = expr,
            preferredClassName = preferredName,
            field = dataclassFieldFromExpr,
        )
    }

    /** True if [expr] appears directly as an argument to a call whose callee resolves to a non-builtin class. */
    private fun isAlreadyWrappedInCustomType(expr: PyExpression): Boolean {
        val kwArg = PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java, false)
        val argExpr: PyExpression = when {
            kwArg != null && kwArg.valueExpression == expr -> kwArg
            else -> expr
        }

        val argList = PsiTreeUtil.getParentOfType(argExpr, PyArgumentList::class.java, false) ?: return false
        val call = argList.parent as? PyCallExpression ?: return false
        val calleeRef = call.callee as? PyReferenceExpression ?: return false
        val resolved = calleeRef.reference.resolve()
        val resolvedClass = resolved as? PyClass ?: return false
        // Treat record-like constructors (dataclass / pydantic models) as containers
        // that we want to transform — they are not considered "already wrapped".
        if (isDataclassClass(resolvedClass)) return false
        val className = resolvedClass.name ?: return false
        return className !in SUPPORTED_BUILTINS
    }

    /**
     * When the intention is started from a call-site expression, try to find
     * the corresponding parameter in the resolved callable and update its
     * annotation from the builtin (e.g. ``str``) to the newly introduced
     * custom type. This keeps annotations in the same logical scope in sync
     * with the wrapped argument.
     */
    private fun updateParameterAnnotationFromCallSite(
        expr: PyExpression,
        newTypeName: String,
        generator: PyElementGenerator,
    ) {
        // Locate the argument list and enclosing call for the expression.
        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java, false) ?: return
        val call = argList.parent as? PyCallExpression ?: return
        val callee = call.callee as? PyReferenceExpression ?: return
        val resolved = callee.reference.resolve() as? PyFunction ?: return

        // Map the expression back to the corresponding parameter, handling
        // the straightforward positional and keyword cases covered by tests.
        val parameter: PyNamedParameter =
            when (val kwArg = PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java, false)) {
                null -> {
                    val args = argList.arguments.toList()
                    val positionalArgs = args.filter { it !is PyKeywordArgument }
                    val posIndex = positionalArgs.indexOf(expr)
                    if (posIndex < 0) return

                    val params = resolved.parameterList.parameters
                    if (posIndex >= params.size) return
                    params[posIndex] as? PyNamedParameter ?: return
                }

                else -> {
                    if (kwArg.valueExpression != expr) return
                    val name = kwArg.keyword ?: return
                    resolved.parameterList.findParameterByName(name) ?: return
                }
            }

        val annotation = parameter.annotation ?: return
        val annExpr = annotation.value ?: return

        val replacement = generator.createExpressionFromText(LanguageLevel.getLatest(), newTypeName)

        // For call‑site initiated intentions we always fully replace the
        // annotation's value expression with the new custom type. This keeps
        // the produced text simple and predictable (``s: Customstr``), which
        // is exactly what tests assert.
        annExpr.replace(replacement)
    }

    /** True if the given field belongs to a @dataclass-decorated class. */
    private fun isDataclassField(field: PyTargetExpression): Boolean {
        val pyClass = PsiTreeUtil.getParentOfType(field, PyClass::class.java) ?: return false
        return isDataclassClass(pyClass)
    }

    /**
     * True if the given class is treated as a "record-like" container that we
     * support for field-based wrapping. For now this includes:
     * - dataclasses (via @dataclass / @dataclasses.dataclass), and
     * - Pydantic models (subclasses of BaseModel).
     */
    private fun isDataclassClass(pyClass: PyClass): Boolean {
        // 1) Dataclass via decorator (@dataclass or @dataclasses.dataclass)
        val decorators = pyClass.decoratorList?.decorators
        if (decorators != null && decorators.any { decorator ->
                val name = decorator.name
                val qName = decorator.qualifiedName?.toString()
                name == "dataclass" || qName == "dataclasses.dataclass" || (qName != null && qName.endsWith(".dataclass"))
            }
        ) {
            return true
        }

        // 2) Pydantic BaseModel subclass via inheritance. We deliberately keep
        // this simple and conservative:
        // - treat any direct base named "BaseModel" as a Pydantic model
        //   (common pattern: ``from pydantic import BaseModel``), and
        // - treat qualified names ending with ".BaseModel" (e.g.
        //   ``pydantic.BaseModel``) as Pydantic.
        val superExprs = pyClass.superClassExpressions
        return superExprs != null && superExprs.any { superExpr ->
            val ref = superExpr as? PyReferenceExpression
            val name = ref?.name
            val qNameOwner = superExpr as? PyQualifiedNameOwner
            val qName = qNameOwner?.qualifiedName
            name == "BaseModel" || (qName != null && qName.endsWith(".BaseModel"))
        }
    }

    /**
     * If the caret expression is part of a dataclass constructor call, resolve
     * the corresponding dataclass field. Supports both keyword and positional
     * arguments for simple dataclass signatures.
     */
    private fun findDataclassFieldForExpression(expr: PyExpression): PyTargetExpression? {
        // Keyword argument: Data(field=expr)
        val kwArg = PsiTreeUtil.getParentOfType(expr, PyKeywordArgument::class.java, false)
        if (kwArg != null && kwArg.valueExpression == expr) {
            val call = PsiTreeUtil.getParentOfType(kwArg, PyCallExpression::class.java, true) ?: return null
            val callee = call.callee as? PyReferenceExpression ?: return null
            val resolved = callee.reference.resolve() as? PyClass ?: return null
            if (!isDataclassClass(resolved)) return null
            val fieldName = kwArg.keyword ?: return null
            val field = resolved.classAttributes.firstOrNull { it.name == fieldName } ?: return null
            return field
        }

        // Positional argument: Data(expr, ...)
        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java, false) ?: return null
        val call = argList.parent as? PyCallExpression ?: return null
        val callee = call.callee as? PyReferenceExpression ?: return null
        val resolved = callee.reference.resolve() as? PyClass ?: return null
        if (!isDataclassClass(resolved)) return null

        val args = argList.arguments.toList()
        val positionalArgs = args.filter { it !is PyKeywordArgument }
        val posIndex = positionalArgs.indexOf(expr)
        if (posIndex < 0) return null

        val fields = resolved.classAttributes
        if (posIndex >= fields.size) return null
        return fields[posIndex]
    }

    /**
     * Wrap all constructor usages of the given dataclass field with the
     * provided wrapper type. This is intentionally conservative and only
     * handles straightforward keyword and positional arguments, matching the
     * scenarios covered by tests.
     */
    private fun wrapDataclassConstructorUsages(
        file: PyFile,
        field: PyTargetExpression,
        wrapperTypeName: String,
        generator: PyElementGenerator,
    ) {
        val pyClass = PsiTreeUtil.getParentOfType(field, PyClass::class.java) ?: return
        val fieldName = field.name ?: return

        val calls = PsiTreeUtil.collectElementsOfType(file, PyCallExpression::class.java)
        for (call in calls) {
            val callee = call.callee as? PyReferenceExpression ?: continue
            val resolved = callee.reference.resolve()
            if (resolved != pyClass) continue

            val argList = call.argumentList ?: continue

            // 1) Prefer keyword arguments matching the field name.
            val keywordArg = argList.arguments
                .filterIsInstance<PyKeywordArgument>()
                .firstOrNull { it.keyword == fieldName }
            if (keywordArg != null) {
                val valueExpr = keywordArg.valueExpression ?: continue
                wrapArgumentExpressionIfNeeded(valueExpr, wrapperTypeName, generator)
                continue
            }

            // 2) Fallback: positional arguments mapped by field index.
            val fields = pyClass.classAttributes
            val fieldIndex = fields.indexOfFirst { it.name == fieldName }
            if (fieldIndex < 0) continue

            val allArgs = argList.arguments.toList()
            val positionalArgs = allArgs.filter { it !is PyKeywordArgument }
            if (fieldIndex >= positionalArgs.size) continue

            val valueExpr = positionalArgs[fieldIndex] ?: continue
            wrapArgumentExpressionIfNeeded(valueExpr, wrapperTypeName, generator)
        }
    }

    private fun wrapArgumentExpressionIfNeeded(
        expr: PyExpression,
        wrapperTypeName: String,
        generator: PyElementGenerator,
    ) {
        // Avoid double-wrapping when the argument is already wrapped with the
        // custom type, e.g. Productid(Productid(123)).
        val existingCall = expr as? PyCallExpression
        if (existingCall != null) {
            val calleeText = existingCall.callee?.text
            if (calleeText == wrapperTypeName) return
        }

        val wrapped = generator.createExpressionFromText(
            LanguageLevel.getLatest(),
            "$wrapperTypeName(${expr.text})",
        )
        expr.replace(wrapped)
    }

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
                wrapDataclassConstructorUsages(refFile, field, wrapperTypeName, generator)

                // Ensure the new type is imported where needed.
                val anchor = PsiTreeUtil.findChildOfType(refFile, PyTypedElement::class.java)
                    ?: refFile.firstChild as? PyTypedElement
                if (anchor != null) {
                    imports.ensureImportedIfNeeded(refFile, anchor, introducedClass)
                }
            }
    }

    /**
     * Derive a PascalCase class name from an identifier such as ``product_id``
     * or ``my_arg``. To keep changes minimal, we currently only derive a name
     * when the identifier contains an underscore so that simple names like
     * ``val`` continue to use the existing builtin-based naming.
     */
    private fun deriveClassNameFromIdentifier(identifier: String): String? {
        if (!identifier.contains('_')) return null

        // Convert snake_case to PascalCase, e.g. ``product_id`` -> ``ProductId``.
        val parts = identifier.split('_').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        return parts.joinToString(separator = "") { part ->
            part.replaceFirstChar { it.uppercaseChar() }
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
