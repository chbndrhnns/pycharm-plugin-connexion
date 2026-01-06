package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.search.PyTestDetection
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

internal object InlineParameterObjectTarget {

    fun find(element: PsiElement): PyFunction? {
        val function = element as? PyFunction ?: PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        if (function != null) {
            val inName = function.nameIdentifier?.let { PsiTreeUtil.isAncestor(it, element, false) } == true
            // In newer Python PSI versions, the parameter *type annotation* might not be a strict descendant of
            // `PyParameterList` in the PSI tree. For refactoring/action availability we still want to treat
            // caret positions within any parameter (including its annotation) as being "in parameters".
            val inParameters = PsiTreeUtil.isAncestor(function.parameterList, element, false) ||
                    (PsiTreeUtil.getParentOfType(element, PyParameter::class.java, false)?.let { param ->
                        function.parameterList.parameters.contains(param)
                    } == true) ||
                    // Fallback: some annotation PSI nodes are attached outside of the parameter list subtree but still
                    // live within its text range (e.g., `arg: int`).
                    function.parameterList.textRange.contains(element.textRange)
            val inReturnAnnotation = function.annotation?.let { PsiTreeUtil.isAncestor(it, element, false) } == true

            if (element == function || inName || inParameters || inReturnAnnotation) {
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

        val virtualFile = function.containingFile.virtualFile
        if (virtualFile != null) {
            val fileIndex = ProjectFileIndex.getInstance(function.project)
            if (fileIndex.isInLibraryClasses(virtualFile) || fileIndex.isInLibrarySource(virtualFile)) {
                return false
            }
        }

        if (function.containingFile.name.endsWith(".pyi")) return false
        if (PyTestDetection.isTestFunction(function)) return false
        if (PyTestDetection.isPytestFixture(function)) return false

        return PyInlineParameterObjectProcessor.hasInlineableParameterObject(function)
    }
}
