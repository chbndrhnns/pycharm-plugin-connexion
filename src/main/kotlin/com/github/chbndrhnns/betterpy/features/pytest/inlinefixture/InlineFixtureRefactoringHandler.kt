package com.github.chbndrhnns.betterpy.features.pytest.inlinefixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyNamedParameter

class InlineFixtureRefactoringHandler : RefactoringActionHandler {

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is PyFile) return
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val analyzer = InlineFixtureAnalyzer(project, file)
        val model = analyzer.analyze(element)
        if (model == null) {
            Messages.showErrorDialog(project, "No pytest fixture found at caret", "Inline Pytest Fixture")
            return
        }

        if (model.isParametrized) {
            Messages.showErrorDialog(project, "Cannot inline parametrized fixture", "Inline Pytest Fixture")
            return
        }

        if (model.hasMultipleReturns) {
            Messages.showErrorDialog(
                project,
                "Cannot inline fixture with multiple return/yield statements",
                "Inline Pytest Fixture"
            )
            return
        }

        if (model.usages.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "No usages found for fixture '${model.fixtureName}'",
                "Inline Pytest Fixture"
            )
            return
        }

        val warnings = buildList {
            if (model.isYieldFixture) {
                add("Fixture uses yield; teardown code after yield will be lost.")
            }
            if (model.scope != null && model.scope != PytestFixtureUtil.PytestFixtureScope.FUNCTION) {
                add("Fixture has ${model.scope.value} scope; inlining will change semantics.")
            }
            if (model.isAutouse) {
                add("Fixture is autouse; inlining removes implicit setup for other tests.")
            }
        }
        if (warnings.isNotEmpty()) {
            val result = Messages.showYesNoDialog(
                project,
                warnings.joinToString("\n") + "\n\nContinue?",
                "Inline Pytest Fixture",
                Messages.getWarningIcon()
            )
            if (result != Messages.YES) return
        }

        val invokedFromParameter = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java, false) != null
        val defaultMode = if (invokedFromParameter) {
            InlineMode.INLINE_THIS_ONLY
        } else {
            InlineMode.INLINE_ALL_AND_REMOVE
        }

        val options = if (model.usages.size > 1) {
            val dialog = InlineFixtureDialog(project, model.fixtureName, model.usages.size, defaultMode)
            if (!dialog.showAndGet()) return
            dialog.getOptions()
        } else {
            InlineFixtureOptions(inlineMode = defaultMode)
        }

        val targetUsage = if (options.inlineMode == InlineMode.INLINE_THIS_ONLY) {
            val parameter = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java, false)
            if (parameter != null) {
                model.usages.firstOrNull { it.parameter?.isEquivalentTo(parameter) == true }
            } else {
                null
            }
        } else {
            null
        }

        InlineFixtureProcessor(
            project = project,
            model = model,
            options = options,
            targetUsage = targetUsage
        ).run()
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Not supported from project view
    }
}
