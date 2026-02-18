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
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyUnionType
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
            val file = target.containingFile as? PyFile ?: return

            val inferredType = context.getType(target)

            val assignedValue = (target.parent as? PyAssignmentStatement)?.assignedValue
            val typeText = if (assignedValue is PyNoneLiteralExpression) {
                "None"
            } else {
                renderTypeForFinal(inferredType, file)
            }

            val finalTypeText = "Final[$typeText]"

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

        /**
         * Render a PyType as annotation text suitable for Final[...].
         * Handles deduplication of union members, parameterized collections,
         * and qualifying types that are not directly imported in the file.
         */
        private fun renderTypeForFinal(type: com.jetbrains.python.psi.types.PyType?, file: PyFile): String {
            return when (type) {
                null -> "Any"
                is PyUnionType -> renderUnionForFinal(type, file)
                is PyCollectionType -> renderCollectionForFinal(type, file)
                is PyClassType -> renderClassForFinal(type, file)
                else -> type.name ?: "Any"
            }
        }

        private fun renderUnionForFinal(union: PyUnionType, file: PyFile): String {
            val parts = LinkedHashSet<String>()
            var hasNone = false

            fun collect(t: com.jetbrains.python.psi.types.PyType?) {
                when (t) {
                    null -> {}
                    is PyUnionType -> t.members.forEach { collect(it) }
                    else -> {
                        if (isNoneType(t)) {
                            hasNone = true
                        } else {
                            parts += renderTypeForFinal(t, file)
                        }
                    }
                }
            }
            union.members.forEach { collect(it) }

            val items = parts.toMutableList()
            if (hasNone) items += "None"

            return if (items.isEmpty()) "Any" else items.joinToString(" | ")
        }

        private fun renderCollectionForFinal(col: PyCollectionType, file: PyFile): String {
            val name = qualifyTypeName(col, file)
            val params = col.elementTypes.map { renderTypeForFinal(it, file) }
            return if (params.isEmpty()) name else "$name[${params.joinToString(", ")}]"
        }

        private fun renderClassForFinal(cls: PyClassType, file: PyFile): String {
            if (isNoneType(cls)) return "None"
            if (cls is PyCollectionType) return renderCollectionForFinal(cls, file)
            return qualifyTypeName(cls, file)
        }

        private fun qualifyTypeName(cls: PyClassType, file: PyFile): String {
            val shortName = cls.name ?: return "Any"
            val qName = cls.classQName ?: return shortName

            // Builtins don't need qualification
            if (qName.startsWith("builtins.")) return shortName

            // Check if the short name is directly imported in the file
            val isDirectlyImported = file.fromImports.any { imp ->
                imp.importElements.any { ie -> ie.name == shortName }
            } || file.importTargets.any { it.name == shortName }

            if (isDirectlyImported) return shortName

            // Check if a parent module is imported (e.g., `import re` makes `re.Pattern` available)
            val parts = qName.split(".")
            for (i in parts.size - 2 downTo 0) {
                val moduleName = parts[i]
                val isModuleImported = file.importTargets.any { it.name == moduleName } ||
                        file.fromImports.any { imp ->
                            imp.importElements.any { ie -> ie.name == moduleName }
                        }
                if (isModuleImported) {
                    return parts.subList(i, parts.size).joinToString(".")
                }
            }

            return shortName
        }

        private fun isNoneType(type: com.jetbrains.python.psi.types.PyType?): Boolean {
            if (type !is PyClassType) return false
            val qName = type.classQName
            return qName != null && PyNames.NONE.contains(qName)
        }
    }
}
