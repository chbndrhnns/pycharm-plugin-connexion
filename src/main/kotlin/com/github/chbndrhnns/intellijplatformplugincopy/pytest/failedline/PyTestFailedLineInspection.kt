package com.github.chbndrhnns.intellijplatformplugincopy.pytest.failedline

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.testIntegration.TestFailedLineManager
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStatement

class PyTestFailedLineInspection : LocalInspectionTool() {

    override fun getStaticDescription(): String = "Highlights the line in the editor where a pytest test failed"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!PluginSettingsState.instance().state.enableHighlightFailedTestLine) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : PyElementVisitor() {
            override fun visitPyFunction(node: PyFunction) {
                checkElement(node, holder)
            }

            override fun visitPyCallExpression(node: PyCallExpression) {
                checkElement(node, holder)
            }

            override fun visitPyStatement(node: PyStatement) {
                checkElement(node, holder)
            }
        }
    }

    private fun checkElement(element: PsiElement, holder: ProblemsHolder) {
        val manager = TestFailedLineManager.getInstance(element.project)
        val info = manager.getTestInfo(element)
        if (info != null) {
            holder.registerProblem(
                element,
                "Test failed",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                null,
                *emptyArray<LocalQuickFix>()
            )
        }
    }
}
