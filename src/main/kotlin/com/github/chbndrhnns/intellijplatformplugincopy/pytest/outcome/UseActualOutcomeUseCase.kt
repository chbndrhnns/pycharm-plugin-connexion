package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

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
        if (file !is PyFile) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val assertStatement = PsiTreeUtil.getParentOfType(element, PyAssertStatement::class.java) ?: return false

        val expression = assertStatement.arguments.firstOrNull()
        if (expression !is PyBinaryExpression || !expression.isOperator("==")) return false

        val pyFunction = PsiTreeUtil.getParentOfType(assertStatement, PyFunction::class.java) ?: return false

        val name = pyFunction.name ?: return false
        if (!name.startsWith("test_")) return false

        // Only show intention if diff data exists for this test
        val locationUrls = PytestLocationUrlFactory.fromPyFunction(pyFunction)
        if (locationUrls.isEmpty()) return false
        return diffService.findWithKeys(locationUrls, explicitKey = null) != null
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
