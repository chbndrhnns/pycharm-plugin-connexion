package com.github.chbndrhnns.betterpy.features.intentions.pytest

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.features.pytest.fixture.PytestFixtureFeatureToggle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class ConvertUsefixturesToTestArgumentIntention : IntentionAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Convert to test argument"
    override fun getFamilyName(): String = "Convert usefixtures to test argument"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PytestFixtureFeatureToggle.isEnabled()) return false
        if (file !is PyFile) return false

        val context = findContext(file, editor) ?: return false
        return context.fixtureName != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return
        val context = findContext(file, editor) ?: return
        val fixtureName = context.fixtureName ?: return
        val decorator = context.decorator
        val function = context.function
        val generator = PyElementGenerator.getInstance(project)

        // Add parameter to function
        val newParam = generator.createParameter(fixtureName)
        function.parameterList.addParameter(newParam)

        // Remove the fixture name from usefixtures
        val argList = decorator.argumentList ?: return
        val args = argList.arguments
        val targetArg = args.firstOrNull {
            it is PyStringLiteralExpression && it.stringValue == fixtureName
        } ?: return

        if (args.size == 1) {
            // Only fixture — remove entire decorator
            decorator.delete()
        } else {
            targetArg.delete()
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.DIFF

    private fun findContext(file: PsiFile, editor: Editor): UsefixturesContext? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null

        // Find the string literal the caret is on
        val stringLiteral = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression::class.java, false)

        // Find the decorator
        val decorator = PsiTreeUtil.getParentOfType(element, PyDecorator::class.java, false) ?: return null
        val qualifiedName = decorator.callee?.let { (it as? PyQualifiedExpression)?.asQualifiedName()?.toString() }
        if (qualifiedName != "pytest.mark.usefixtures") return null

        val function = PsiTreeUtil.getParentOfType(decorator, PyFunction::class.java) ?: return null
        if (!PytestNaming.isTestFunction(function)) return null

        // If caret is on a string argument, use that; otherwise use the first string argument
        val fixtureName = stringLiteral?.stringValue
            ?: decorator.argumentList?.arguments
                ?.filterIsInstance<PyStringLiteralExpression>()
                ?.firstOrNull()?.stringValue
            ?: return null
        return UsefixturesContext(decorator, function, fixtureName)
    }

    private data class UsefixturesContext(
        val decorator: PyDecorator,
        val function: PyFunction,
        val fixtureName: String?
    )
}
