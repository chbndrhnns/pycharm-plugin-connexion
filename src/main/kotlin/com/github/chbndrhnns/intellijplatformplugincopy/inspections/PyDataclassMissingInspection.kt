package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.python.PythonVersionGuard
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.util.isOwnCode
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.TypeEvalContext

class PyDataclassMissingInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        if (!PluginSettingsState.instance().state.enableDataclassMissingInspection) {
            return object : PyElementVisitor() {}
        }
        if (!PythonVersionGuard.isSatisfied(holder.project)) {
            return object : PyElementVisitor() {}
        }
        return object : PyElementVisitor() {
            val context = TypeEvalContext.codeAnalysis(holder.project, holder.file)

            override fun visitPyClass(node: PyClass) {
                super.visitPyClass(node)

                // Use the standard helper to check if the class is already a dataclass
                if (parseDataclassParameters(node, context) != null) return

                val superClasses = node.getSuperClasses(context)

                for (superClass in superClasses) {
                    // Check if superclass is a standard dataclass (from `dataclasses` module)
                    val params = parseDataclassParameters(superClass, context)
                    if (params != null && params.type.asPredefinedType == PyDataclassParameters.PredefinedType.STD) {
                        val nameIdentifier = node.nameIdentifier ?: return
                        holder.registerProblem(
                            nameIdentifier,
                            "Subclass of a dataclass should also be decorated with @dataclass",
                            PyAddDataclassQuickFix()
                        )
                        // Report only once per class
                        return
                    }
                }
            }
        }
    }

    private class PyAddDataclassQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = PluginConstants.ACTION_PREFIX + "Add @dataclass decorator"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val pyClass = element.parent as? PyClass ?: return
            val file = pyClass.containingFile as? PyFile ?: return
            val generator = PyElementGenerator.getInstance(project)

            var decoratorText = "@dataclass"
            var needImport = true

            // Check for existing "from dataclasses import dataclass" to reuse alias
            val fromImport = file.fromImports.firstOrNull { stmt ->
                !stmt.isStarImport &&
                        stmt.importSourceQName?.toString() == "dataclasses" &&
                        stmt.importElements.any { it.importedQName?.toString() == "dataclass" }
            }

            if (fromImport != null) {
                needImport = false
                val importElement = fromImport.importElements.first { it.importedQName?.toString() == "dataclass" }
                if (importElement.asName != null) {
                    decoratorText = "@${importElement.asName}"
                }
            } else {
                // Check for existing "import dataclasses" to reuse alias
                val importTarget = file.importTargets.firstOrNull {
                    it.importedQName?.toString() == "dataclasses"
                }
                if (importTarget != null) {
                    needImport = false
                    decoratorText = if (importTarget.asName != null) {
                        "@${importTarget.asName}.dataclass"
                    } else {
                        "@dataclasses.dataclass"
                    }
                }
            }

            // Add import if needed
            if (needImport) {
                AddImportHelper.addOrUpdateFromImportStatement(
                    file,
                    "dataclasses",
                    "dataclass",
                    null,
                    AddImportHelper.ImportPriority.BUILTIN,
                    null
                )
            }

            // Create and add the decorator using PyElementGenerator
            // createDecoratorList returns a list, we take the first (and only) decorator
            val newDecoratorList = generator.createDecoratorList(decoratorText)
            val newDecorator = newDecoratorList.decorators.firstOrNull()

            if (newDecorator != null) {
                val decoratorList = pyClass.decoratorList
                if (decoratorList != null) {
                    decoratorList.add(newDecorator)
                } else {
                    // If no decorators exist, add the whole list before the class name/keyword
                    pyClass.addBefore(newDecoratorList, pyClass.firstChild)
                }
            }
        }
    }
}