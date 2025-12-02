package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

class PyDataclassMissingInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return object : PyElementVisitor() {
            override fun visitPyClass(node: PyClass) {
                super.visitPyClass(node)

                // If the class is already a dataclass, no need to warn
                if (isDataclass(node)) return

                val context = TypeEvalContext.codeAnalysis(node.project, node.containingFile)
                val superClasses = node.getSuperClasses(context)

                for (superClass in superClasses) {
                    if (isDataclass(superClass)) {
                        val nameIdentifier = node.nameIdentifier ?: return
                        holder.registerProblem(
                            nameIdentifier,
                            "Subclass of a dataclass should also be decorated with @dataclass",
                            PyAddDataclassQuickFix()
                        )
                        // We only need to report once per class
                        return
                    }
                }
            }
        }
    }

    private fun isDataclass(pyClass: PyClass): Boolean {
        val decorators = pyClass.decoratorList?.decorators ?: return false
        return decorators.any { decorator ->
            // Check simple name first for speed
            val name = decorator.name
            if (name == "dataclass") return@any true

            // Check qualified name for accuracy (e.g. dataclasses.dataclass)
            val qName = decorator.qualifiedName
            if (qName != null && qName.endsWith("dataclass")) return@any true

            false
        }
    }

    private class PyAddDataclassQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Add @dataclass decorator"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val pyClass = element.parent as? PyClass ?: return
            val file = pyClass.containingFile as? PyFile ?: return
            val generator = PyElementGenerator.getInstance(project)
            val languageLevel = LanguageLevel.forElement(file)

            var decoratorText = "@dataclass"
            var needImport = true

            // Check imports to reuse existing ones
            // Check "from dataclasses import dataclass"
            val fromImports = file.fromImports
            for (stmt in fromImports) {
                if (stmt.isStarImport) continue
                if (stmt.importSourceQName?.toString() == "dataclasses") {
                    for (elt in stmt.importElements) {
                        if (elt.importedQName?.toString() == "dataclass") {
                            needImport = false
                            if (elt.asName != null) {
                                decoratorText = "@${elt.asName}"
                            }
                            break
                        }
                    }
                }
                if (!needImport) break
            }

            // If not found, check "import dataclasses"
            if (needImport) {
                val imports = file.importTargets
                for (stmt in imports) {
                    if (stmt.importedQName?.toString() == "dataclasses") {
                        needImport = false
                        decoratorText = if (stmt.asName != null) {
                            "@${stmt.asName}.dataclass"
                        } else {
                            "@dataclasses.dataclass"
                        }
                        break
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
                    null,
                    null
                )
            }

            // Add decorator
            val decoratorList = pyClass.decoratorList
            if (decoratorList != null) {
                val dummyClass =
                    generator.createFromText(languageLevel, PyClass::class.java, "$decoratorText\nclass Dummy: pass")
                val decorator = dummyClass.decoratorList?.decorators?.firstOrNull()
                if (decorator != null) {
                    decoratorList.add(decorator)
                }
            } else {
                // Create a new decorator list
                val dummyClass =
                    generator.createFromText(languageLevel, PyClass::class.java, "$decoratorText\nclass Dummy: pass")
                val newDecoratorList = dummyClass.decoratorList
                if (newDecoratorList != null) {
                    pyClass.addBefore(newDecoratorList, pyClass.firstChild)
                }
            }
        }
    }
}
