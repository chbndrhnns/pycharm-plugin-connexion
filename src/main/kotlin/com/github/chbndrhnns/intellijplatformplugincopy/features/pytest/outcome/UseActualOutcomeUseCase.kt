package com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.outcome

import com.github.chbndrhnns.intellijplatformplugincopy.core.pytest.PytestNaming
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyAssertStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction

class UseActualOutcomeUseCase(
    private val diffService: TestOutcomeDiffService,
    private val replacementEngine: OutcomeReplacementEngine = OutcomeReplacementEngine(),
) {

    fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        LOG.debug("UseActualOutcomeUseCase.isAvailable: checking availability")

        if (file !is PyFile) {
            LOG.debug("UseActualOutcomeUseCase.isAvailable: file is not PyFile, returning false")
            return false
        }

        val element = file.findElementAt(editor.caretModel.offset)
        if (element == null) {
            LOG.debug("UseActualOutcomeUseCase.isAvailable: no element at caret offset ${editor.caretModel.offset}, returning false")
            return false
        }

        val assertStatement = PsiTreeUtil.getParentOfType(element, PyAssertStatement::class.java)
        if (assertStatement == null) {
            LOG.debug("UseActualOutcomeUseCase.isAvailable: no PyAssertStatement parent found, returning false")
            return false
        }

        val expression = assertStatement.arguments.firstOrNull()
        if (expression !is PyBinaryExpression || !expression.isOperator("==")) {
            LOG.debug("UseActualOutcomeUseCase.isAvailable: expression is not a binary == expression, returning false")
            return false
        }

        val pyFunction = PsiTreeUtil.getParentOfType(assertStatement, PyFunction::class.java)
        if (pyFunction == null) {
            LOG.debug("UseActualOutcomeUseCase.isAvailable: no PyFunction parent found, returning false")
            return false
        }

        val name = pyFunction.name
        if (name == null) {
            LOG.debug("UseActualOutcomeUseCase.isAvailable: function name is null, returning false")
            return false
        }

        if (!PytestNaming.isTestFunctionName(name)) {
            LOG.debug("UseActualOutcomeUseCase.isAvailable: function name '$name' does not start with 'test_', returning false")
            return false
        }

        // Only show intention if diff data exists for this test
        val locationUrls = PytestLocationUrlFactory.fromPyFunction(pyFunction)
        LOG.debug("UseActualOutcomeUseCase.isAvailable: generated location URLs: $locationUrls")

        if (locationUrls.isEmpty()) {
            LOG.debug("UseActualOutcomeUseCase.isAvailable: no location URLs generated, returning false")
            return false
        }

        val availableKeys = diffService.getAllKeys()
        LOG.debug("UseActualOutcomeUseCase.isAvailable: available diff keys in service: $availableKeys")

        val diffResult = diffService.findWithKeys(locationUrls, explicitKey = null)
        val isAvailable = diffResult != null

        if (isAvailable) {
            LOG.debug("UseActualOutcomeUseCase.isAvailable: diff data found with key '${diffResult?.second}', returning true")
        } else {
            LOG.debug("UseActualOutcomeUseCase.isAvailable: no diff data found for any location URL, returning false")
        }

        return isAvailable
    }

    companion object {
        private val LOG = Logger.getInstance(UseActualOutcomeUseCase::class.java)
    }

    fun invoke(project: Project, editor: Editor, file: PsiFile, explicitTestKey: String?) {
        if (file !is PyFile) return
        val element = file.findElementAt(editor.caretModel.offset) ?: return

        val assertStatement = PsiTreeUtil.getParentOfType(element, PyAssertStatement::class.java) ?: return

        val expression = assertStatement.arguments.firstOrNull()
        if (expression !is PyBinaryExpression || !expression.isOperator("==")) return

        val pyFunction = PsiTreeUtil.getParentOfType(assertStatement, PyFunction::class.java) ?: return

        val locationUrls = PytestLocationUrlFactory.fromPyFunction(pyFunction)
        if (locationUrls.isEmpty()) return
        val (diff, matchedKey) = diffService.findWithKeys(locationUrls, explicitTestKey) ?: return

        replacementEngine.apply(project, assertStatement, diff, matchedKey)
    }
}
