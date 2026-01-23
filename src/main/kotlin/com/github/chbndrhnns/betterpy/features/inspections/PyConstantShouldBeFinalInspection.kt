package com.github.chbndrhnns.betterpy.features.inspections

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyConstantShouldBeFinalInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        val settings = PluginSettingsState.instance().state
        if (!settings.enableConstantFinalInspection) {
            return object : PyElementVisitor() {}
        }

        return object : PyElementVisitor() {
            override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
                if (!isUserCode(node.containingFile as? PyFile)) return

                // Handle only simple name targets: FOO = ...
                // Ignore multiple targets: FOO = BAR = 1
                val targets = node.targets
                if (targets.size != 1) return

                val target = targets[0] as? PyTargetExpression ?: return
                if (target.isQualified) return // Ignore obj.FOO = ...

                val name = target.name ?: return
                if (!isConstantName(name)) return

                // Skip enum members - they are capitalized but not constants
                val containingClass = target.containingClass
                if (containingClass != null) {
                    val context = TypeEvalContext.codeAnalysis(holder.project, holder.file)
                    if (isEnum(containingClass, context)) return
                }

                if (isAlreadyFinal(target)) return

                holder.registerProblem(
                    target,
                    "Constant '$name' should be declared as Final",
                    WrapConstantInFinalQuickFix()
                )
            }

            private fun isUserCode(file: PyFile?): Boolean {
                if (file == null) return false
                val vFile = file.virtualFile ?: return false
                val index = ProjectRootManager.getInstance(file.project).fileIndex
                return index.isInSourceContent(vFile) &&
                        !index.isInLibraryClasses(vFile) &&
                        !index.isInLibrarySource(vFile)
            }

            private fun isConstantName(name: String): Boolean {
                if (name.isEmpty()) return false
                if (!name[0].isUpperCase() && name[0] != '_') return false
                if (name.all { it == '_' }) return false
                if (!name.any { it.isLetter() }) return false
                return name.all { it.isUpperCase() || it.isDigit() || it == '_' }
            }

            private fun isAlreadyFinal(target: PyTargetExpression): Boolean {
                val annotation = target.annotation
                if (annotation != null) {
                    val annotationText = annotation.text
                    if (annotationText.contains("Final")) return true
                }
                return false
            }

            private fun isEnum(cls: PyClass, context: TypeEvalContext): Boolean {
                val metaClassType = cls.getMetaClassType(true, context)
                return metaClassType is PyClassType &&
                        metaClassType.pyClass.isSubclass(PyNames.TYPE_ENUM_META, context)
            }
        }
    }

    private class WrapConstantInFinalQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = PluginConstants.ACTION_PREFIX + "Wrap constant type in Final"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val target = descriptor.psiElement as? PyTargetExpression ?: return
            val pyElementGenerator = PyElementGenerator.getInstance(project)
            val context = TypeEvalContext.codeAnalysis(project, target.containingFile)

            val inferredType = context.getType(target)
            var typeText = inferredType?.name ?: "Any"

            val assignedValue = (target.parent as? PyAssignmentStatement)?.assignedValue
            if (assignedValue is PyNoneLiteralExpression) {
                typeText = "None"
            }

            val finalTypeText = "Final[$typeText]"

            val file = target.containingFile as? PyFile ?: return

            // Ensure Final is imported
            val hasFinal =
                file.fromImports.any { it.importSourceQName == QualifiedName.fromDottedString("typing") && it.importElements.any { ie -> ie.name == "Final" } } ||
                        file.fromImports.any { it.importSourceQName == QualifiedName.fromDottedString("typing_extensions") && it.importElements.any { ie -> ie.name == "Final" } }

            if (!hasFinal) {
                val importStatement = pyElementGenerator.createFromImportStatement(
                    LanguageLevel.forElement(target),
                    "typing",
                    "Final",
                    null
                )
                val firstImport = file.importBlock.firstOrNull()
                if (firstImport != null) {
                    file.addBefore(importStatement, firstImport)
                } else {
                    file.addBefore(importStatement, file.firstChild)
                }
            }

            // Rewrite annotation
            val assignment = target.parent as? PyAssignmentStatement ?: return
            val value = assignment.assignedValue?.text ?: "..."

            val newStatement = pyElementGenerator.createFromText(
                LanguageLevel.forElement(target),
                PyAssignmentStatement::class.java,
                "${target.name}: $finalTypeText = $value"
            )
            val replaced = assignment.replace(newStatement)
            com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project).reformat(replaced)
        }
    }
}
