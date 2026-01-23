package com.github.chbndrhnns.betterpy.features.pytest.outcome

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testIntegration.TestFailedLineManager
import com.jetbrains.python.psi.PyFunction

class PyTestFailedLineManager(private val project: Project) : TestFailedLineManager {

    private val diffService: TestOutcomeDiffService
        get() = TestOutcomeDiffService.getInstance(project)

    override fun getTestInfo(element: PsiElement): TestFailedLineManager.TestInfo? {
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return null

        val locationUrls = PytestLocationUrlFactory.fromPyFunction(pyFunction)
        val (diff, _) = diffService.findWithKeys(locationUrls, null) ?: return null

        if (diff.failedLine == -1) return null

        val document = element.containingFile.viewProvider.document ?: return null
        val startLine = document.getLineNumber(element.textRange.startOffset) + 1 // 1-based
        val endLine = document.getLineNumber(element.textRange.endOffset) + 1 // 1-based

        if (diff.failedLine in startLine..endLine) {
            return object : TestFailedLineManager.TestInfo {
                override fun getErrorMessage(): String = "Test failed"
                override fun getTopStackTraceLine(): String? = null
                override fun getMagnitude(): Int = 0
            }
        }

        return null
    }

    override fun getRunQuickFix(element: PsiElement): LocalQuickFix? = null

    override fun getDebugQuickFix(element: PsiElement, editor: String?): LocalQuickFix? = null

    companion object {
        fun getInstance(project: Project): PyTestFailedLineManager =
            project.getService(TestFailedLineManager::class.java) as PyTestFailedLineManager
    }
}
