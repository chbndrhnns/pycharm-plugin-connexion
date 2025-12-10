package com.github.chbndrhnns.intellijplatformplugincopy.intention.optional

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class MakeParameterOptionalIntention : IntentionAction, PriorityAction {

    override fun getText(): String = "Make optional"

    override fun getFamilyName(): String = "Make optional"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableMakeParameterOptionalIntention) return false
        if (file !is PyFile) return false

        val element = findTargetElement(editor, file) ?: return false
        val annotation = getAnnotation(element) ?: return false

        // Check if already optional
        val annotationText = annotation.text
        if (annotationText.contains("None") || annotationText.contains("Optional")) {
            return false
        }

        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = findTargetElement(editor, file) ?: return
        val generator = PyElementGenerator.getInstance(project)

        val annotation = getAnnotation(element) ?: return
        val currentAnnotationText = annotation.value?.text ?: "Any"
        val newAnnotationText = "$currentAnnotationText | None"

        if (element is PyNamedParameter) {
            val defaultValueText = if (element.hasDefaultValue()) element.defaultValue!!.text else "None"
            val newParam = generator.createParameter(
                element.name!!,
                defaultValueText,
                newAnnotationText,
                LanguageLevel.getDefault()
            )
            element.replace(newParam)
        } else if (element is PyTargetExpression) {
            val defaultValueText =
                if (element.findAssignedValue() != null) element.findAssignedValue()!!.text else "None"
            val newStatementText = "${element.name}: $newAnnotationText = $defaultValueText"

            // We need to replace the statement containing the target expression
            val parentStatement = PsiTreeUtil.getParentOfType(
                element,
                PyAssignmentStatement::class.java,
                PyTypeDeclarationStatement::class.java
            )

            if (parentStatement != null) {
                val newStatement = generator.createFromText(
                    LanguageLevel.getDefault(),
                    PyAssignmentStatement::class.java,
                    newStatementText
                )
                parentStatement.replace(newStatement)
            } else {
                // Fallback: try to replace the element itself if it's standalone (unlikely for valid code)
                // or just update annotation and assignment manually if parent logic fails
                // But for now, let's assume parentStatement is found for valid fields
            }
        }
    }

    override fun startInWriteAction(): Boolean = true

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    private fun findTargetElement(editor: Editor, file: PsiFile): PyElement? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null

        val param = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java)
        if (param != null) return param

        val target = PsiTreeUtil.getParentOfType(element, PyTargetExpression::class.java)
        if (target != null) {
            // Check if it's a field in a class
            if (PsiTreeUtil.getParentOfType(target, PyClass::class.java) != null) {
                return target
            }
        }
        return null
    }

    private fun getAnnotation(element: PyElement): PyAnnotation? {
        return when (element) {
            is PyNamedParameter -> element.annotation
            is PyTargetExpression -> element.annotation
            else -> null
        }
    }
}
