package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.search.PyTestDetection
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

internal object IntroduceParameterObjectTarget {

    fun find(element: PsiElement): PyFunction? {
        val function = element as? PyFunction ?: PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        if (function != null) {
            val inName = function.nameIdentifier?.let { PsiTreeUtil.isAncestor(it, element, false) } == true
            val inParameters = PsiTreeUtil.isAncestor(function.parameterList, element, false)

            if (element == function || inName || inParameters) {
                return function
            }
        }

        val argumentList = PsiTreeUtil.getParentOfType(element, PyArgumentList::class.java, false)
        val call = (argumentList?.parent as? PyCallExpression)
            ?: PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java, false)

        val callee = call?.callee as? PyReferenceExpression ?: return null
        val resolved = callee.reference.resolve() as? PyFunction ?: return null

        return resolved
    }

    fun isAvailable(element: PsiElement): Boolean {
        val function = find(element) ?: return false

        if (function.containingFile.name.endsWith(".pyi")) return false

        if (PyTestDetection.isTestFunction(function)) return false
        if (PyTestDetection.isPytestFixture(function)) return false

        val parameters = function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }

        return parameters.isNotEmpty()
    }
}