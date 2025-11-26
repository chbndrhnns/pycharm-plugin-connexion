package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.isPositionalOnlyCallable
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.TypeEvalContext
import javax.swing.Icon

/**
 * Intention that populates missing **required** call arguments (including keyword-only
 * parameters) at the caret with placeholder ellipsis values, e.g.
 *
 *     A(<caret>) -> A(x=..., y=...)
 *
 * Optional parameters (those with default values) are left untouched so the
 * generated call stays minimal.
 */
class PopulateRequiredArgumentsIntention : IntentionAction, HighPriorityAction, DumbAware, Iconable {

    private var text: String = "Populate required arguments with '...'"

    override fun getText(): String = text

    override fun getFamilyName(): String = "Populate arguments"

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enablePopulateRequiredArgumentsIntention) {
            return false
        }

        if (file !is PyFile) return false

        val call = findCallExpression(editor, file) ?: return false

        val ctx = TypeEvalContext.codeAnalysis(project, file)

        // Do not offer for positional-only function calls
        if (isPositionalOnlyCallable(call)) return false

        val missing = getMissingRequiredParameters(call, ctx)
        if (missing.isEmpty()) return false

        text = "Populate required arguments with '...'"
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val pyFile = file as? PyFile ?: return
        val call = findCallExpression(editor, pyFile) ?: return

        val ctx = TypeEvalContext.userInitiated(project, pyFile)
        val missing = getMissingRequiredParameters(call, ctx)
        if (missing.isEmpty()) return

        val generator = PyElementGenerator.getInstance(project)
        val argumentList = call.argumentList ?: return
        val languageLevel = LanguageLevel.forElement(pyFile)

        for (param in missing) {
            val name = param.name ?: continue
            val arg: PyKeywordArgument = generator.createKeywordArgument(languageLevel, name, "...")
            argumentList.addArgument(arg)
        }
    }

    override fun startInWriteAction(): Boolean = true

    private fun findCallExpression(editor: Editor, file: PyFile): PyCallExpression? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java, /* strict = */ false)
    }

    private fun getMissingRequiredParameters(
        call: PyCallExpression,
        context: TypeEvalContext,
    ): List<PyCallableParameter> {
        val resolveContext = PyResolveContext.defaultContext(context)
        val mappings = call.multiMapArguments(resolveContext)
        if (mappings.isEmpty()) return emptyList()

        val mapping = mappings.first()
        val callableType: PyCallableType = mapping.callableType ?: return emptyList()

        val allParams = callableType.getParameters(context) ?: return emptyList()
        val mapped = mapping.mappedParameters

        return allParams
            .asSequence()
            .filter { !it.isSelf }
            .filter { !it.isPositionalContainer && !it.isKeywordContainer }
            .filter { param -> !mapped.values.contains(param) }
            .filter { param -> !param.hasDefaultValue() }
            .toList()
    }
}
