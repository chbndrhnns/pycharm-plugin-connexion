package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.lang.CodeInsightActions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext

private const val GOTO_SUPER_FIXTURE_TITLE = "Choose super fixture"

class PytestFixtureGotoSuperHandler : CodeInsightActionHandler {
    private val log = Logger.getInstance(PytestFixtureGotoSuperHandler::class.java)

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            if (log.isDebugEnabled) {
                log.debug("PytestFixtureGotoSuperHandler: feature disabled, delegating to default handler")
            }
            delegateToDefault(project, editor, file, null)
            return
        }
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)
        val function = element?.let { PsiTreeUtil.getParentOfType(it, PyFunction::class.java, false) }
        if (function == null || !PytestFixtureUtil.isFixtureFunction(function)) {
            if (log.isDebugEnabled) {
                log.debug("PytestFixtureGotoSuperHandler: no fixture at caret, delegating to default handler")
            }
            delegateToDefault(project, editor, file, element)
            return
        }

        val fixtureName = PytestFixtureUtil.getFixtureName(function)
        if (fixtureName == null) {
            if (log.isDebugEnabled) {
                log.debug("PytestFixtureGotoSuperHandler: missing fixture name, delegating to default handler")
            }
            delegateToDefault(project, editor, file, element)
            return
        }

        val context = TypeEvalContext.codeAnalysis(project, function.containingFile)
        val parents = PytestFixtureResolver.findParentFixtures(function, fixtureName, context)
        if (parents.isEmpty()) {
            if (log.isDebugEnabled) {
                log.debug("PytestFixtureGotoSuperHandler: no parent fixtures for '$fixtureName', delegating to default handler")
            }
            delegateToDefault(project, editor, file, element)
            return
        }
        if (log.isDebugEnabled) {
            log.debug("PytestFixtureGotoSuperHandler: showing ${parents.size} parent fixture(s) for '$fixtureName'")
        }

        val targets = LinkedHashSet<PsiElement>()
        for (link in parents) {
            targets.add(link.fixtureFunction)
        }

        val targetArray = targets.toTypedArray()
        if (targetArray.size == 1) {
            if (log.isDebugEnabled) {
                log.debug("PytestFixtureGotoSuperHandler: navigating to single parent for '$fixtureName'")
            }
            (targetArray[0] as? Navigatable)?.navigate(true)
            return
        }

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(targetArray.toList())
            .setRenderer(com.intellij.ide.util.DefaultPsiElementCellRenderer())
            .setTitle(GOTO_SUPER_FIXTURE_TITLE)
            .setItemChosenCallback { (it as? Navigatable)?.navigate(true) }
            .createPopup()
        popup.showInBestPositionFor(editor)
    }

    private fun delegateToDefault(project: Project, editor: Editor, file: PsiFile, element: PsiElement?) {
        val language = element?.let { PsiUtilCore.getLanguageAtOffset(file, it.textOffset) } ?: file.language
        val handlers = CodeInsightActions.GOTO_SUPER.allForLanguage(language)
        val fallback = handlers.firstOrNull { it.javaClass != this.javaClass }
        fallback?.invoke(project, editor, file)
    }
}
