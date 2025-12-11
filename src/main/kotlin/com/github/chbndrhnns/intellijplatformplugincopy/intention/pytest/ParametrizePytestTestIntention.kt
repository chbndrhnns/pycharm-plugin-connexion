package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*

class ParametrizePytestTestIntention : IntentionAction, HighPriorityAction {

    override fun getText(): String = "Parametrize pytest test"
    override fun getFamilyName(): String = "Parametrize pytest test"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableParametrizePytestTestIntention) return false
        if (file !is PyFile) return false

        val pyFunction = findEnclosingFunction(file, editor.caretModel.offset) ?: return false

        val name = pyFunction.name ?: return false
        if (!name.startsWith("test_")) return false

        if (isAlreadyParametrized(pyFunction)) return false

        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return

        val pyFunction = findEnclosingFunction(file, editor.caretModel.offset) ?: return

        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.getLatest()

        ensurePytestImported(file)
        addParametrizeDecorator(pyFunction, generator)
        addFirstParameter(pyFunction, generator, languageLevel)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.DIFF

    /**
     * Find the `PyFunction` at caret using PSI utilities, ignoring decorators etc.
     */
    private fun findEnclosingFunction(file: PyFile, offset: Int): PyFunction? {
        val element = file.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, PyFunction::class.java, /* strict = */ false)
    }

    /**
     * True if the function already has a decorator that resolves to `pytest.mark.parametrize`.
     */
    private fun isAlreadyParametrized(pyFunction: PyFunction): Boolean {
        val decorators = pyFunction.decoratorList?.decorators ?: return false
        return decorators.any { decorator ->
            resolvesToPytestParametrize(decorator)
        }
    }

    private fun resolvesToPytestParametrize(decorator: PyDecorator): Boolean {
        val callee = decorator.callee as? PyQualifiedExpression ?: return false
        val qName = callee.asQualifiedName()?.toString() ?: return false

        return qName == "pytest.mark.parametrize" ||
                qName == "_pytest.mark.parametrize" ||
                qName.endsWith(".pytest.mark.parametrize")
    }

    /** Ensure that `import pytest` is present (uses built‑in import helper). */
    private fun ensurePytestImported(file: PyFile) {
        AddImportHelper.addImportStatement(
            file,
            "pytest",
            null,
            AddImportHelper.ImportPriority.THIRD_PARTY,
            null,
        )
    }

    /** Add `@pytest.mark.parametrize("arg", [])` before existing decorators using PSI generator. */
    private fun addParametrizeDecorator(
        pyFunction: PyFunction,
        generator: PyElementGenerator,
    ) {
        val decoratorText = "@pytest.mark.parametrize(\"arg\", [])"
        val existingDecoratorList = pyFunction.decoratorList

        val newDecoratorList: PyDecoratorList = if (existingDecoratorList != null) {
            // Rebuild the whole decorator list via PSI instead of concatenating strings manually
            val existingTexts = existingDecoratorList.decorators.map(PsiElement::getText)
            val allTexts = listOf(decoratorText) + existingTexts
            generator.createDecoratorList(*allTexts.toTypedArray())
        } else {
            generator.createDecoratorList(decoratorText)
        }

        if (existingDecoratorList != null) {
            existingDecoratorList.replace(newDecoratorList)
        } else {
            pyFunction.addBefore(newDecoratorList, pyFunction.firstChild)
        }
    }

    /** Add a first parameter `arg` using the PSI generator. */
    private fun addFirstParameter(
        pyFunction: PyFunction,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
    ) {
        val parameterList = pyFunction.parameterList
        val firstParam = generator.createParameter("arg", null, null, languageLevel)

        val existingParams = parameterList.parameters
        if (existingParams.isEmpty()) {
            parameterList.add(firstParam)
        } else {
            parameterList.addBefore(firstParam, existingParams.first())
        }
    }

}