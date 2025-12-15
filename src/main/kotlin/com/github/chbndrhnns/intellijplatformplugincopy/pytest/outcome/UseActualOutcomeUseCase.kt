package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyAssertStatement
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
        val pyFunction = PsiTreeUtil.getParentOfType(assertStatement, PyFunction::class.java) ?: return false

        val name = pyFunction.name ?: return false
        if (!name.startsWith("test_")) return false

        val locationUrl = PytestLocationUrlFactory.fromPyFunction(pyFunction) ?: return false
        return diffService.hasAnyForBaseLocationUrl(locationUrl)
    }

    fun invoke(project: Project, editor: Editor, file: PsiFile, explicitTestKey: String?) {
        if (file !is PyFile) return
        val element = file.findElementAt(editor.caretModel.offset) ?: return

        val assertStatement = PsiTreeUtil.getParentOfType(element, PyAssertStatement::class.java) ?: return
        val pyFunction = PsiTreeUtil.getParentOfType(assertStatement, PyFunction::class.java) ?: return

        val locationUrl = PytestLocationUrlFactory.fromPyFunction(pyFunction) ?: return
        val (diff, matchedKey) = diffService.findWithKey(locationUrl, explicitTestKey) ?: return

        replacementEngine.apply(project, assertStatement, diff, matchedKey)
    }
}
