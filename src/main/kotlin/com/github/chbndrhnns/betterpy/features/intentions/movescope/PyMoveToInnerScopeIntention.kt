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

        // Check for top-level function → move into class
        if (isAvailableForFunction(element)) return true

        // Check for top-level class → move into another class
        if (isAvailableForClass(element)) return true

        return false
    }

    private fun isAvailableForFunction(element: PsiElement): Boolean {
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
        if (!MoveScopeTextBuilder.canInsertMethodIntoClass(funcName, targetClass)) return false

        return true
    }

    private fun isAvailableForClass(element: PsiElement): Boolean {
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java) ?: return false
        val nameIdentifier = pyClass.nameIdentifier ?: return false
        if (!PsiTreeUtil.isAncestor(nameIdentifier, element, false)) return false

        // Must be a top-level class
        val outerClass = PsiTreeUtil.getParentOfType(pyClass, PyClass::class.java, true)
        if (outerClass != null) return false
        val enclosingFunc = PsiTreeUtil.getParentOfType(pyClass, PyFunction::class.java, true)
        if (enclosingFunc != null) return false

        val file = pyClass.containingFile as? PyFile ?: return false
        val className = pyClass.name ?: return false

        // Must have at least one other top-level class in the file to move into
        val otherClasses = file.topLevelClasses.filter { it !== pyClass }
        if (otherClasses.isEmpty()) return false

        // Find the target class
        val targetClass = findTargetClassForClass(pyClass, otherClasses) ?: return false

        // D5: Name collision — check if target class already has a nested class with the same name
        if (hasNameCollision(className, targetClass)) return false

        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        // Try function first
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        if (pyFunction != null && pyFunction.containingClass == null) {
            val nameId = pyFunction.nameIdentifier
            if (nameId != null && PsiTreeUtil.isAncestor(nameId, element, false)) {
                val file = pyFunction.containingFile as? PyFile ?: return
                val classes = file.topLevelClasses
                val targetClass = findTargetClass(pyFunction, classes) ?: return
                PyMoveFunctionIntoClassProcessor(project, pyFunction, targetClass).run()
                return
            }
        }

        // Try class
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        if (pyClass != null && PsiTreeUtil.getParentOfType(pyClass, PyClass::class.java, true) == null) {
            val nameId = pyClass.nameIdentifier
            if (nameId != null && PsiTreeUtil.isAncestor(nameId, element, false)) {
                val file = pyClass.containingFile as? PyFile ?: return
                val otherClasses = file.topLevelClasses.filter { it !== pyClass }
                val targetClass = findTargetClassForClass(pyClass, otherClasses) ?: return
                PyMoveClassIntoClassProcessor(project, pyClass, targetClass).run()
                return
            }
        }
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
        fun hasNameCollision(name: String, targetClass: PyClass): Boolean {
            return targetClass.statementList.statements.any { stmt ->
                (stmt is PyFunction && stmt.name == name) ||
                    (stmt is PyClass && stmt.name == name)
            }
        }

        /**
         * Find the target class for moving a top-level class into.
         * If there's exactly one other top-level class, use it.
         */
        fun findTargetClassForClass(classToMove: PyClass, otherClasses: List<PyClass>): PyClass? {
            if (otherClasses.size == 1) return otherClasses[0]
            return null
        }
    }
}
