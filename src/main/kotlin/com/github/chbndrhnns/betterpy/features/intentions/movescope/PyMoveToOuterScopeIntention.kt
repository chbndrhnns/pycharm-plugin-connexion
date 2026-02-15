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

class PyMoveToOuterScopeIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName() = "Move to outer scope"
    override fun getText() = PluginConstants.ACTION_PREFIX + "Move to outer scope"

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!PluginSettingsState.instance().state.enableMoveToOuterScopeIntention) return false
        if (!element.isOwnCode()) return false
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java) ?: return false
        // Only available when caret is on the class name identifier of a nested class
        val nameIdentifier = pyClass.nameIdentifier ?: return false
        if (!PsiTreeUtil.isAncestor(nameIdentifier, element, false)) return false
        // Must be nested inside another class
        val outerClass = PsiTreeUtil.getParentOfType(pyClass, PyClass::class.java, true) ?: return false
        val nestedName = pyClass.name ?: return false
        // Check for name collision at target scope
        val targetScope = PsiTreeUtil.getParentOfType(outerClass, PyClass::class.java, true)
        if (targetScope != null) {
            // Moving into another class — check sibling nested classes
            if (targetScope.statementList.statements.filterIsInstance<PyClass>()
                    .any { it !== outerClass && it.name == nestedName }) return false
        } else {
            // Moving to top-level — check top-level classes
            val file = pyClass.containingFile as? PyFile ?: return false
            if (file.topLevelClasses.any { it.name == nestedName }) return false
        }
        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java) ?: return
        PyMoveToOuterScopeProcessor(project, pyClass).run()
    }
}
