package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.core.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.core.util.isOwnCode
import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils

class ParametrizePytestTestIntention : IntentionAction, HighPriorityAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Parametrize pytest test"
    override fun getFamilyName(): String = "Parametrize pytest test"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableParametrizePytestTestIntention) return false
        if (file !is PyFile) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false

        val name = pyFunction.name ?: return false
        if (!name.startsWith("test_")) return false

        return !isAlreadyParametrized(pyFunction)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return

        ensurePytestImported(file)

        // Use existing helper to add or merge decorator
        PyUtil.addDecorator(pyFunction, "@pytest.mark.parametrize(\"arg\", [])")

        addFirstParameter(pyFunction, project)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.DIFF

    private fun isAlreadyParametrized(pyFunction: PyFunction): Boolean {
        val decorators = pyFunction.decoratorList?.decorators ?: return false
        return decorators.any { decorator ->
            val callee = decorator.callee as? PyQualifiedExpression
            val qName = callee?.asQualifiedName()?.toString()
            qName == "pytest.mark.parametrize" ||
                    qName == "_pytest.mark.parametrize" ||
                    qName?.endsWith(".pytest.mark.parametrize") == true
        }
    }

    private fun ensurePytestImported(file: PyFile) {
        AddImportHelper.addImportStatement(
            file,
            "pytest",
            null,
            AddImportHelper.ImportPriority.THIRD_PARTY,
            null
        )
    }

    private fun addFirstParameter(pyFunction: PyFunction, project: Project) {
        val generator = PyElementGenerator.getInstance(project)
        val parameterList = pyFunction.parameterList
        val firstParam = generator.createParameter("arg")

        val existingParams = parameterList.parameters

        // Find anchor: first non-self/cls parameter to insert before
        val nonSelfClsAnchor = existingParams.firstOrNull { param ->
            val name = param.name
            name != "self" && name != "cls"
        }

        if (nonSelfClsAnchor != null) {
            // Insert before the anchor.
            // isFirst=true ensures no comma is added *before* our new param (assuming existing comma structure is valid)
            // isLast=false ensures a comma is added *after* our new param (separating it from the anchor)
            PyUtil.addListNode(parameterList, firstParam, nonSelfClsAnchor.node, true, false, true)
        } else {
            // Append at the end (after self/cls or in empty list)
            val rpar = parameterList.node.findChildByType(PyTokenTypes.RPAR)
            if (rpar != null) {
                // Determine if we need a comma before the new parameter
                val prev = PyPsiUtils.getPrevNonWhitespaceSibling(rpar)
                val hasTrailingComma = prev?.elementType == PyTokenTypes.COMMA
                val isListEmpty = existingParams.isEmpty()

                // If list is empty or already has a trailing comma, we don't need a leading comma for the new item
                val isFirst = isListEmpty || hasTrailingComma

                // Insert before ')'
                PyUtil.addListNode(parameterList, firstParam, rpar, isFirst, true, true)
            } else {
                // Fallback for malformed parameter list
                parameterList.addParameter(firstParam)
            }
        }
    }
}