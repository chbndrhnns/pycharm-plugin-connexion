package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStatement

class PyTestFailedLineInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PyElementVisitor() {
            override fun visitPyFunction(node: PyFunction) {
                checkElement(node, holder, isOnTheFly)
            }

            override fun visitElement(element: com.intellij.psi.PsiElement) {
                super.visitElement(element)
                if (element is PyStatement && element !is PyFunction) {
                    checkGeneralElement(element, holder, isOnTheFly)
                }
            }
        }
    }

    private fun checkGeneralElement(element: com.intellij.psi.PsiElement, holder: ProblemsHolder, isOnTheFly: Boolean) {
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return
        val project = element.project
        val diffService = TestOutcomeDiffService.getInstance(project)
        val locationUrls = PytestLocationUrlFactory.fromPyFunction(pyFunction)
        LOG.debug("PyTestFailedLineInspection.checkGeneralElement: checking element $element in function ${pyFunction.name}, locationUrls=$locationUrls")
        val (diff, _) = diffService.findWithKeys(locationUrls, null) ?: run {
            LOG.debug("PyTestFailedLineInspection.checkGeneralElement: no diff found for locationUrls=$locationUrls")
            return
        }

        if (diff.failedLine != -1) {
            val document = element.containingFile.viewProvider.document ?: return
            val startLine = document.getLineNumber(element.textRange.startOffset) + 1
            val endLine = document.getLineNumber(element.textRange.endOffset) + 1
            LOG.debug("PyTestFailedLineInspection.checkGeneralElement: diff.failedLine=${diff.failedLine}, element lines=$startLine..$endLine")

            if (diff.failedLine in startLine..endLine) {
                LOG.debug("PyTestFailedLineInspection.checkGeneralElement: registering problem at line ${diff.failedLine}")
                val problemDescriptor = holder.manager.createProblemDescriptor(
                    element,
                    "Test failed at this line",
                    null as Array<com.intellij.codeInspection.LocalQuickFix>?,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    isOnTheFly,
                    false
                )
                problemDescriptor.setTextAttributes(CodeInsightColors.RUNTIME_ERROR)
                holder.registerProblem(problemDescriptor)
            }
        }
    }

    private fun checkElement(element: PyFunction, holder: ProblemsHolder, isOnTheFly: Boolean) {
        val project = element.project
        val diffService = TestOutcomeDiffService.getInstance(project)
        val locationUrls = PytestLocationUrlFactory.fromPyFunction(element)
        LOG.debug("PyTestFailedLineInspection.checkElement: checking function ${element.name}, locationUrls=$locationUrls")
        val (diff, _) = diffService.findWithKeys(locationUrls, null) ?: run {
            LOG.debug("PyTestFailedLineInspection.checkElement: no diff found for locationUrls=$locationUrls")
            return
        }

        if (diff.failedLine != -1) {
            val document = element.containingFile.viewProvider.document ?: return

            // If the failed line is the function definition line, highlight the function name
            val functionLine = document.getLineNumber(element.textRange.startOffset) + 1
            LOG.debug("PyTestFailedLineInspection.checkElement: diff.failedLine=${diff.failedLine}, functionLine=$functionLine")
            if (functionLine == diff.failedLine) {
                val nameIdentifier = element.nameIdentifier ?: return
                LOG.debug("PyTestFailedLineInspection.checkElement: registering problem for function name at line $functionLine")
                val problemDescriptor = holder.manager.createProblemDescriptor(
                    nameIdentifier,
                    "Test failed at this line",
                    null as Array<com.intellij.codeInspection.LocalQuickFix>?,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    isOnTheFly,
                    false
                )
                problemDescriptor.setTextAttributes(CodeInsightColors.RUNTIME_ERROR)
                holder.registerProblem(problemDescriptor)
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(PyTestFailedLineInspection::class.java)
    }
}
