package com.github.chbndrhnns.intellijplatformplugincopy.features.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.core.python.PythonVersionGuard
import com.github.chbndrhnns.intellijplatformplugincopy.core.util.isOwnCode
import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype.isDataclassClass
import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.populate.PyDataclassFieldExtractor
import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.shared.JbPopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.shared.PopupHost
import com.intellij.codeInspection.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Inspection that elevates unresolved references from warnings to errors.
 * This helps enforce stricter code quality by treating unresolved references as errors.
 * 
 * For qualified references (e.g., obj.attr), the inspection checks if the qualifier's type
 * is in the allowlist (dataclasses and pydantic.BaseModel by default). If so, unresolved
 * attributes are also marked as errors.
 */
class PyUnresolvedReferenceAsErrorInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        if (!PythonVersionGuard.isSatisfied(holder.project)) {
            return object : PyElementVisitor() {}
        }
        val settings = PluginSettingsState.instance().state
        if (!settings.enableUnresolvedReferenceAsErrorInspection) {
            return object : PyElementVisitor() {}
        }

        val context = TypeEvalContext.codeAnalysis(holder.project, holder.file)

        return object : PyElementVisitor() {
            override fun visitPyReferenceExpression(node: PyReferenceExpression) {
                super.visitPyReferenceExpression(node)

                val reference = node.reference
                if (reference.resolve() == null) {
                    val qualifier = node.qualifier
                    val name = node.name

                    // Skip dunder symbols (special module-level attributes like __name__, __file__, etc.)
                    if (name != null && isDunderSymbol(name)) {
                        return
                    }

                    if (qualifier == null) {
                        val quickfixes = mutableListOf<LocalQuickFix>()
                        if (name != null) {
                            val pyFile = node.containingFile as? PyFile
                            if (pyFile != null) {
                                val importedModules = findImportedModules(pyFile)
                                val candidates = mutableListOf<String>()
                                for (module in importedModules) {
                                    val attr = module.findTopLevelAttribute(name)
                                    val clazz = module.findTopLevelClass(name)
                                    val func = module.findTopLevelFunction(name)
                                    if (attr != null || clazz != null || func != null) {
                                        val moduleName = module.name.substringBeforeLast(".py")
                                        if (moduleName != "__init__") {
                                            candidates.add(moduleName)
                                        }
                                    }
                                }
                                if (candidates.isNotEmpty()) {
                                    quickfixes.add(QualifyReferenceQuickFix(candidates))
                                }
                            }
                        }

                        holder.registerProblem(
                            node,
                            "Unresolved reference '${node.name}'",
                            ProblemHighlightType.ERROR,
                            *quickfixes.toTypedArray()
                        )
                    } else {
                        if (isQualifierTypeInAllowlist(qualifier, context)) {
                            // Check if the attribute name matches a field alias
                            if (!isFieldAlias(qualifier, name, context)) {
                                // Report the problem on the name identifier only, not the entire qualified expression
                                val nameElement = node.nameElement?.psi
                                if (nameElement != null) {
                                    holder.registerProblem(
                                        nameElement,
                                        "Unresolved attribute '${node.name}'",
                                        ProblemHighlightType.ERROR
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findImportedModules(file: PyFile): List<PyFile> {
        val result = mutableSetOf<PyFile>()

        file.importTargets.forEach { importTarget ->
            val results = importTarget.multiResolve()
            results.forEach { resolveResult ->
                val resolved = resolveResult.element
                if (resolved is PyFile) {
                    result.add(resolved)
                }
            }
        }

        file.fromImports.forEach { fromImport ->
            // from module import ...
            val resolvedSource = fromImport.resolveImportSource()
            if (resolvedSource is PyFile) {
                result.add(resolvedSource)
            } else if (resolvedSource is com.intellij.psi.PsiDirectory) {
                // If it's a directory, it might be a package.
                // For 'from . import domain', domain might be a file in that directory.
                fromImport.importElements.forEach { importElement ->
                    val name = importElement.importedQName?.lastComponent
                    if (name != null) {
                        val child = resolvedSource.findFile("$name.py") ?: resolvedSource.findSubdirectory(name)
                            ?.findFile("__init__.py")
                        if (child is PyFile) {
                            result.add(child)
                        }
                    }
                }
            }

            // from . import module
            fromImport.importElements.forEach { importElement ->
                // Try resolving the reference of the import element itself
                val reference = importElement.reference
                if (reference != null) {
                    val resolvedElement = reference.resolve()
                    if (resolvedElement is PyFile) {
                        result.add(resolvedElement)
                    } else if (reference is com.intellij.psi.PsiPolyVariantReference) {
                        val resolveResults = reference.multiResolve(false)
                        for (resolveResult in resolveResults) {
                            val element = resolveResult.element
                            if (element is PyFile) {
                                result.add(element)
                            }
                        }
                    }
                }
            }
        }
        return result.toList()
    }

    /**
     * Hooks for testing the QualifyReferenceQuickFix.
     * Allows injecting a fake PopupHost for unit tests.
     */
    object QualifyReferenceQuickFixHooks {
        var popupHost: PopupHost? = null
    }

    class QualifyReferenceQuickFix(private val moduleNames: List<String>) : LocalQuickFix {
        override fun getFamilyName(): String {
            return if (moduleNames.size == 1) "Qualify with '${moduleNames[0]}.'"
            else "Qualify reference..."
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? PyReferenceExpression ?: return
            val name = element.name ?: return

            if (moduleNames.size == 1) {
                applyQualification(project, element, moduleNames[0], name)
            } else {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
                val popupHost = QualifyReferenceQuickFixHooks.popupHost ?: JbPopupHost()
                popupHost.showChooser(
                    editor = editor,
                    title = "Qualify with",
                    items = moduleNames,
                    render = { "$it.$name" },
                    onChosen = { chosenModuleName ->
                        WriteCommandAction.runWriteCommandAction(project, familyName, null, {
                            applyQualification(project, element, chosenModuleName, name)
                        }, element.containingFile)
                    }
                )
            }
        }

        private fun applyQualification(project: Project, element: PyReferenceExpression, moduleName: String, name: String) {
            val generator = PyElementGenerator.getInstance(project)
            val qualifiedExpression =
                generator.createExpressionFromText(LanguageLevel.forElement(element), "$moduleName.$name")
            element.replace(qualifiedExpression)
        }
    }

    /**
     * Checks if a name is a dunder symbol (starts and ends with double underscores).
     * These are special Python attributes like __name__, __file__, __all__, etc.
     */
    private fun isDunderSymbol(name: String): Boolean {
        return name.startsWith("__") && name.endsWith("__") && name.length > 4
    }

    /**
     * Checks if the qualifier's type is in the allowlist (dataclasses and pydantic.BaseModel).
     */
    private fun isQualifierTypeInAllowlist(qualifier: PyExpression, context: TypeEvalContext): Boolean {
        val qualifierType = context.getType(qualifier) ?: return false
        val classType = qualifierType as? PyClassType ?: return false
        val pyClass = classType.pyClass
        return isDataclassClass(pyClass)
    }

    /**
     * Checks if the given attribute name matches a field alias in the qualifier's class.
     * Returns true if the name is a valid alias for any field in the dataclass/pydantic model.
     */
    private fun isFieldAlias(qualifier: PyExpression, attributeName: String?, context: TypeEvalContext): Boolean {
        if (attributeName == null) return false

        val qualifierType = context.getType(qualifier) ?: return false
        val classType = qualifierType as? PyClassType ?: return false
        val pyClass = classType.pyClass

        if (!isDataclassClass(pyClass)) return false

        // Extract fields and check if any field has this attribute name as an alias
        val extractor = PyDataclassFieldExtractor()
        val fields = extractor.extractDataclassFields(pyClass, context)

        return fields.any { field -> field.aliasName == attributeName }
    }
}
