package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.core.MyBundle
import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFunction

/**
 * Line marker provider that shows a gutter icon on functions that are candidates
 * for the "Introduce Parameter Object" refactoring.
 *
 * This helps users discover the refactoring by providing a visual indicator
 * on functions with multiple parameters that could benefit from grouping.
 */
class ParameterObjectLineMarkerProvider : LineMarkerProvider {

    companion object {
        private val tooltipText = MyBundle.message("introduce.parameter.object.title")
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (!PluginSettingsState.instance().state.parameterObject.enableParameterObjectGutterIcon) return null
        if (!PluginSettingsState.instance().state.parameterObject.enableParameterObjectRefactoring) return null

        // Only process PyFunction elements to avoid duplicate markers
        if (element !is PyFunction) return null

        // Check if this function is a valid candidate for the refactoring
        if (!IntroduceParameterObjectTarget.isAvailable(element)) return null

        // Don't show icon if function already has a parameter object
        if (PyInlineParameterObjectProcessor.hasInlineableParameterObject(element)) return null

        // Use the name identifier as the anchor element for the marker
        val anchor = element.nameIdentifier ?: return null

        return LineMarkerInfo(
            anchor,
            anchor.textRange,
            AllIcons.Actions.RefactoringBulb,
            { tooltipText },
            { _, psiElement ->
                // Find the function from the anchor element and invoke the action
                val function = psiElement.parent as? PyFunction ?: return@LineMarkerInfo
                invokeRefactoringAction(function)
            },
            GutterIconRenderer.Alignment.RIGHT,
            { tooltipText }
        )
    }

    private fun invokeRefactoringAction(function: PyFunction) {
        val project = function.project
        val file = function.containingFile
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return

        // Move caret to the function name so the handler can find it
        val nameIdentifier = function.nameIdentifier ?: return
        editor.caretModel.moveToOffset(nameIdentifier.textOffset)

        val handler = IntroduceParameterObjectRefactoringHandler()
        handler.invoke(project, editor, file, null)
    }
}
