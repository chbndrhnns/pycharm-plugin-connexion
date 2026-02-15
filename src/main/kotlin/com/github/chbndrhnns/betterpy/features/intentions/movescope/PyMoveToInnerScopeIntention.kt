package com.github.chbndrhnns.betterpy.features.intentions.movescope

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter

class PyMoveToInnerScopeIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName() = "Move to inner scope"
    override fun getText() = PluginConstants.ACTION_PREFIX + "Move to inner scope"

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!PluginSettingsState.instance().state.enableMoveToInnerScopeIntention) return false
        if (!element.isOwnCode()) return false

        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false
        val nameIdentifier = pyFunction.nameIdentifier ?: return false
        if (!PsiTreeUtil.isAncestor(nameIdentifier, element, false)) return false

        // Must be a top-level function (not inside a class or another function)
        if (pyFunction.containingClass != null) return false
        val enclosingFunc = PsiTreeUtil.getParentOfType(pyFunction, PyFunction::class.java, true)
        if (enclosingFunc != null) return false

        val file = pyFunction.containingFile as? PyFile ?: return false
        val funcName = pyFunction.name ?: return false

        // Must have at least one class in the file to move into
        val classes = file.topLevelClasses
        if (classes.isEmpty()) return false

        // Find the target class
        val targetClass = findTargetClass(pyFunction, classes) ?: return false

        // D5: Name collision — check if target class already has a method with the same name
        if (hasNameCollision(funcName, targetClass)) return false

        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return
        val file = pyFunction.containingFile as? PyFile ?: return
        val classes = file.topLevelClasses

        val targetClass = findTargetClass(pyFunction, classes) ?: return

        PyMoveFunctionIntoClassProcessor(project, pyFunction, targetClass).run()
    }

    companion object {
        /**
         * Find the target class for moving the function into.
         * If the first parameter is typed as a class in the file, use that class.
         * Otherwise, if there's exactly one class in the file, use it (for @staticmethod).
         */
        fun findTargetClass(function: PyFunction, classes: List<PyClass>): PyClass? {
            val params = function.parameterList.parameters
            if (params.isNotEmpty()) {
                val firstParam = params[0] as? PyNamedParameter
                if (firstParam != null) {
                    val annotationText = firstParam.annotation?.value?.text
                    if (annotationText != null) {
                        val matchingClass = classes.find { it.name == annotationText }
                        if (matchingClass != null) return matchingClass
                    }
                }
            }

            // No typed first param matching a class — use the single class if there's exactly one
            if (classes.size == 1) return classes[0]

            return null
        }

        /**
         * Check if the target class already has a method with the given name.
         */
        fun hasNameCollision(funcName: String, targetClass: PyClass): Boolean {
            return targetClass.statementList.statements.any { stmt ->
                stmt is PyFunction && stmt.name == funcName
            }
        }
    }
}
