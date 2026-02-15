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
import com.jetbrains.python.psi.PyReferenceExpression

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
        // U2/E9: Check if nested class body references outer class members (Outer.something)
        if (referencesOuterMembers(pyClass, outerClass)) return false
        return true
    }

    private fun referencesOuterMembers(nestedClass: PyClass, outerClass: PyClass): Boolean {
        val outerName = outerClass.name ?: return false
        val refs = PsiTreeUtil.findChildrenOfType(nestedClass, PyReferenceExpression::class.java)
        for (ref in refs) {
            val qualifier = ref.qualifier as? PyReferenceExpression ?: continue
            if (qualifier.referencedName != outerName) continue
            // Outer.Something — check if "Something" is a member of the outer class (not the nested class itself)
            val attrName = ref.referencedName ?: continue
            if (attrName == nestedClass.name) continue
            // Check if the outer class has this member
            val outerMembers = outerClass.statementList.statements
            val hasMember = outerMembers.any { stmt ->
                when (stmt) {
                    is PyClass -> stmt.name == attrName
                    is com.jetbrains.python.psi.PyFunction -> stmt.name == attrName
                    is com.jetbrains.python.psi.PyAssignmentStatement -> stmt.targets.any { it.text == attrName }
                    else -> false
                }
            }
            if (hasMember) return true
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java) ?: return
        PyMoveToOuterScopeProcessor(project, pyClass).run()
    }
}
