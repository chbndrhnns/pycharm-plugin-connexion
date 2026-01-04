package com.github.chbndrhnns.intellijplatformplugincopy.pytest.failedline

import com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome.PytestLocationUrlFactory
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.execution.TestStateStorage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testIntegration.TestFailedLineManager
import com.jetbrains.python.psi.PyFunction

class PyTestFailedLineManagerImpl(private val project: Project) : TestFailedLineManager {

    class PyTestInfo(
        private val myMagnitude: Int,
        private val myErrorMessage: String?
    ) : TestFailedLineManager.TestInfo {
        override fun getMagnitude(): Int = myMagnitude
        override fun getErrorMessage(): String? = myErrorMessage
        override fun getTopStackTraceLine(): String? = null
    }

    override fun getTestInfo(element: PsiElement): TestFailedLineManager.TestInfo? {
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false) ?: return null
        val url = getTestUrl(function) ?: return null
        val record = TestStateStorage.getInstance(project).getState(url) ?: return null
        if (record.failedLine == -1) return null

        val document = element.containingFile.viewProvider.document ?: return null
        val elementLine = document.getLineNumber(element.textOffset) + 1

        if (elementLine == record.failedLine) {
            return PyTestInfo(record.magnitude, record.errorMessage)
        }
        return null
    }

    override fun getRunQuickFix(element: PsiElement): LocalQuickFix? = null
    override fun getDebugQuickFix(element: PsiElement, editor: String): LocalQuickFix? =
        null

    private fun getTestUrl(function: PyFunction): String? {
        return PytestLocationUrlFactory.fromPyFunction(function)
    }
}
