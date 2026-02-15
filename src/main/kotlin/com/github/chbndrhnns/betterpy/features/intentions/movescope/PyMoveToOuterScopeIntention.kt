package com.github.chbndrhnns.betterpy.features.intentions.movescope

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class PyMoveToOuterScopeIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName() = "Move to outer scope"
    override fun getText() = PluginConstants.ACTION_PREFIX + "Move to outer scope"

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!PluginSettingsState.instance().state.enableMoveToOuterScopeIntention) return false
        if (!element.isOwnCode()) return false

        // Check for nested class case
        if (isAvailableForNestedClass(element)) return true

        // Check for local function case
        if (isAvailableForLocalFunction(element)) return true

        // Check for method case
        if (isAvailableForMethod(element)) return true

        return false
    }

    private fun isAvailableForNestedClass(element: PsiElement): Boolean {
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java) ?: return false
        val nameIdentifier = pyClass.nameIdentifier ?: return false
        if (!PsiTreeUtil.isAncestor(nameIdentifier, element, false)) return false
        val outerClass = PsiTreeUtil.getParentOfType(pyClass, PyClass::class.java, true) ?: return false
        val nestedName = pyClass.name ?: return false
        // Check for name collision at target scope
        val targetScope = PsiTreeUtil.getParentOfType(outerClass, PyClass::class.java, true)
        if (targetScope != null) {
            if (targetScope.statementList.statements.filterIsInstance<PyClass>()
                    .any { it !== outerClass && it.name == nestedName }) return false
        } else {
            val file = pyClass.containingFile as? PyFile ?: return false
            if (file.topLevelClasses.any { it.name == nestedName }) return false
        }
        if (referencesOuterMembers(pyClass, outerClass)) return false
        return true
    }

    private fun isAvailableForLocalFunction(element: PsiElement): Boolean {
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false
        // Caret must be on the function name identifier
        val nameIdentifier = pyFunction.nameIdentifier ?: return false
        if (!PsiTreeUtil.isAncestor(nameIdentifier, element, false)) return false
        // Must be nested inside another function
        val outerFunction = PsiTreeUtil.getParentOfType(pyFunction, PyFunction::class.java, true) ?: return false
        val funcName = pyFunction.name ?: return false

        // Name collision at module level
        val file = pyFunction.containingFile as? PyFile ?: return false
        if (file.topLevelFunctions.any { it.name == funcName }) return false

        // Block if function uses `nonlocal`
        if (usesNonlocal(pyFunction)) return false

        // Block if function is returned as a closure (function reference, not call)
        if (isReturnedAsClosure(pyFunction, outerFunction)) return false

        return true
    }

    private fun usesNonlocal(function: PyFunction): Boolean {
        val body = function.statementList
        return PsiTreeUtil.findChildrenOfType(body, PyNonlocalStatement::class.java).isNotEmpty()
    }

    private fun isReturnedAsClosure(localFunc: PyFunction, outerFunc: PyFunction): Boolean {
        val funcName = localFunc.name ?: return false
        // Check if the local function has captured variables (reads from enclosing scope)
        val capturedVars = findCapturedVariables(localFunc, outerFunc)
        if (capturedVars.isEmpty()) return false

        // Check if the function name is used as a reference (not a call) in the outer function
        val refs = PsiTreeUtil.findChildrenOfType(outerFunc, PyReferenceExpression::class.java)
        for (ref in refs) {
            if (PsiTreeUtil.isAncestor(localFunc, ref, true)) continue
            if (ref.referencedName != funcName) continue
            // Check if this reference is NOT a call expression's callee
            val parent = ref.parent
            if (parent is PyCallExpression && parent.callee === ref) continue
            // It's used as a bare reference (e.g., `return inner`) — closure semantics
            return true
        }
        return false
    }

    private fun isAvailableForMethod(element: PsiElement): Boolean {
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false
        val nameIdentifier = pyFunction.nameIdentifier ?: return false
        if (!PsiTreeUtil.isAncestor(nameIdentifier, element, false)) return false
        // Must be a method (inside a class, not inside another function)
        val containingClass = pyFunction.containingClass ?: return false
        val enclosingFunc = PsiTreeUtil.getParentOfType(pyFunction, PyFunction::class.java, true)
        if (enclosingFunc != null) return false // local function case handled separately
        val funcName = pyFunction.name ?: return false

        // Name collision at module level
        val file = pyFunction.containingFile as? PyFile ?: return false
        if (file.topLevelFunctions.any { it.name == funcName }) return false

        // Block if method uses super()
        if (usesSuperCall(pyFunction)) return false

        // Block if method is a @property
        if (isPropertyMethod(pyFunction)) return false

        // Block dunder methods
        if (funcName.startsWith("__") && funcName.endsWith("__")) return false

        // Block @staticmethod and @classmethod
        val decorators = pyFunction.decoratorList?.decorators ?: emptyArray()
        if (decorators.any { it.name == "staticmethod" || it.name == "classmethod" }) return false

        // Must have self parameter
        val params = pyFunction.parameterList.parameters
        if (params.isEmpty() || params[0].name != "self") return false

        return true
    }

    private fun usesSuperCall(function: PyFunction): Boolean {
        val calls = PsiTreeUtil.findChildrenOfType(function.statementList, PyCallExpression::class.java)
        return calls.any { call ->
            val callee = call.callee
            callee is PyReferenceExpression && callee.referencedName == "super"
        }
    }

    private fun isPropertyMethod(function: PyFunction): Boolean {
        val decorators = function.decoratorList?.decorators ?: return false
        return decorators.any { dec ->
            val name = dec.name
            val qualifiedName = dec.qualifiedName?.toString()
            name == "property" ||
                qualifiedName?.endsWith(".setter") == true ||
                qualifiedName?.endsWith(".deleter") == true ||
                dec.text.contains(".setter") ||
                dec.text.contains(".deleter")
        }
    }

    private fun referencesOuterMembers(nestedClass: PyClass, outerClass: PyClass): Boolean {
        val outerName = outerClass.name ?: return false
        val refs = PsiTreeUtil.findChildrenOfType(nestedClass, PyReferenceExpression::class.java)
        for (ref in refs) {
            val qualifier = ref.qualifier as? PyReferenceExpression ?: continue
            if (qualifier.referencedName != outerName) continue
            val attrName = ref.referencedName ?: continue
            if (attrName == nestedClass.name) continue
            val outerMembers = outerClass.statementList.statements
            val hasMember = outerMembers.any { stmt ->
                when (stmt) {
                    is PyClass -> stmt.name == attrName
                    is PyFunction -> stmt.name == attrName
                    is PyAssignmentStatement -> stmt.targets.any { it.text == attrName }
                    else -> false
                }
            }
            if (hasMember) return true
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        // Try nested class first
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        if (pyClass != null) {
            val nameId = pyClass.nameIdentifier
            if (nameId != null && PsiTreeUtil.isAncestor(nameId, element, false)) {
                val outerClass = PsiTreeUtil.getParentOfType(pyClass, PyClass::class.java, true)
                if (outerClass != null) {
                    PyMoveToOuterScopeProcessor(project, pyClass).run()
                    return
                }
            }
        }

        // Try local function or method
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        if (pyFunction != null) {
            val nameId = pyFunction.nameIdentifier
            if (nameId != null && PsiTreeUtil.isAncestor(nameId, element, false)) {
                val outerFunction = PsiTreeUtil.getParentOfType(pyFunction, PyFunction::class.java, true)
                if (outerFunction != null) {
                    PyMoveLocalFunctionProcessor(project, pyFunction, outerFunction).run()
                    return
                }
                // Method to top-level function
                if (pyFunction.containingClass != null) {
                    PyMoveMethodToTopLevelProcessor(project, pyFunction).run()
                    return
                }
            }
        }
    }

    companion object {
        /**
         * Find variables used in [localFunc] that are defined in [outerFunc] (captured variables).
         * This includes outer function parameters and local assignments.
         */
        fun findCapturedVariables(localFunc: PyFunction, outerFunc: PyFunction): List<String> {
            // Collect names defined in the local function (params + local assignments)
            val localNames = mutableSetOf<String>()
            localFunc.parameterList.parameters.forEach { param ->
                param.name?.let { localNames.add(it) }
            }
            PsiTreeUtil.findChildrenOfType(localFunc.statementList, PyTargetExpression::class.java).forEach { target ->
                // Only count targets directly in this function, not in nested functions
                val containingFunc = PsiTreeUtil.getParentOfType(target, PyFunction::class.java)
                if (containingFunc === localFunc) {
                    target.name?.let { localNames.add(it) }
                }
            }

            // Collect names available in the outer function scope
            val outerNames = mutableSetOf<String>()
            outerFunc.parameterList.parameters.forEach { param ->
                param.name?.let { outerNames.add(it) }
            }
            // Assignments in outer function (before the local function)
            val localFuncOffset = localFunc.textRange.startOffset
            PsiTreeUtil.findChildrenOfType(outerFunc.statementList, PyTargetExpression::class.java).forEach { target ->
                val containingFunc = PsiTreeUtil.getParentOfType(target, PyFunction::class.java)
                if (containingFunc === outerFunc && target.textRange.startOffset < localFuncOffset) {
                    target.name?.let { outerNames.add(it) }
                }
            }

            // Find references in local function body that refer to outer scope names
            val captured = mutableListOf<String>()
            val refs = PsiTreeUtil.findChildrenOfType(localFunc.statementList, PyReferenceExpression::class.java)
            for (ref in refs) {
                if (ref.qualifier != null) continue // skip qualified refs like obj.attr
                val name = ref.referencedName ?: continue
                if (name in localNames) continue // defined locally
                if (name in outerNames && name !in captured) {
                    captured.add(name)
                }
            }
            return captured
        }
    }
}
