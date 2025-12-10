package com.github.chbndrhnns.intellijplatformplugincopy.intention.optional

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class MakeParameterMandatoryIntention : IntentionAction, PriorityAction {

    override fun getText(): String = "Make mandatory"

    override fun getFamilyName(): String = "Make mandatory"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is PyFile) return false

        val element = findTargetElement(editor, file) ?: return false

        // Check default value is None
        val defaultValueText = when (element) {
            is PyNamedParameter -> element.defaultValue?.text
            is PyTargetExpression -> element.findAssignedValue()?.text
            else -> null
        }

        return defaultValueText == "None"
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = findTargetElement(editor, file) ?: return
        val elementName = element.name ?: return

        val problematicCallSites = findCallSitesWithMissingArgument(element)
        if (problematicCallSites.isNotEmpty()) {
            if (!ApplicationManager.getApplication().isUnitTestMode) {
                val message =
                    "There are call sites that do not provide this argument. Making it mandatory will break code. Call sites will be updated with ellipsis (...). Continue?"
                val result = Messages.showYesNoDialog(
                    project,
                    message,
                    "Complications Found",
                    Messages.getWarningIcon()
                )
                if (result != Messages.YES) {
                    return
                }
            }
        }

        val generator = PyElementGenerator.getInstance(project)

        // Update call sites
        if (element is PyNamedParameter) {
            for (call in problematicCallSites) {
                val argumentList = call.argumentList ?: continue
                val keywordArg = generator.createKeywordArgument(
                    LanguageLevel.getDefault(),
                    elementName,
                    "..."
                )
                argumentList.addArgument(keywordArg)
            }
        }

        val annotation = getAnnotation(element) ?: return
        val currentAnnotationText = annotation.value?.text ?: return

        val newAnnotationText = removeNoneFromAnnotation(currentAnnotationText)

        if (element is PyNamedParameter) {
            val newParam = generator.createParameter(
                elementName,
                null,
                newAnnotationText,
                LanguageLevel.getDefault()
            )
            element.replace(newParam)
        } else if (element is PyTargetExpression) {
            val newStatementText = "$elementName: $newAnnotationText"

            // Handle assignment statement (field = None) -> (field: Type)
            val parent = element.parent
            if (parent is PyAssignmentStatement) {
                val newStatement = generator.createFromText(
                    LanguageLevel.getDefault(),
                    PyTypeDeclarationStatement::class.java,
                    newStatementText
                )
                parent.replace(newStatement)
            }
        }
    }

    private fun findCallSitesWithMissingArgument(element: PyElement): List<PyCallExpression> {
        val function = if (element is PyNamedParameter) {
            PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        } else {
            // For fields, finding the constructor or class usage is harder and context dependent (dataclass etc)
            // We'll skip field usage check for now to avoid false positives/negatives without deep analysis
            null
        } ?: return emptyList()

        val parameter = element as? PyNamedParameter ?: return emptyList()
        val parameterName = parameter.name ?: return emptyList()

        // Find parameter index
        val parameters = function.parameterList.parameters
        val paramIndex = parameters.indexOf(parameter)
        if (paramIndex == -1) return emptyList()

        val references = ReferencesSearch.search(function, function.useScope).findAll()
        val missingSites = mutableListOf<PyCallExpression>()

        for (ref in references) {
            val callElement = ref.element.parent
            if (callElement is PyCallExpression) {
                // Check if argument is provided
                val argumentList = callElement.argumentList ?: continue

                // check keyword arguments
                val keywordArg = argumentList.getKeywordArgument(parameterName)
                if (keywordArg != null) continue

                // check positional arguments
                // This is a simplification. We assume regular args.
                // Handling *args and **kwargs correctly requires resolving.
                // But for a basic check:
                val args = argumentList.arguments
                // Count positional args (those that are not keyword args)
                val positionalArgsCount = args.count { it !is PyKeywordArgument }

                if (positionalArgsCount <= paramIndex) {
                    missingSites.add(callElement)
                }
            }
        }

        return missingSites
    }

    private fun removeNoneFromAnnotation(text: String): String {
        val result = text.trim()

        // 1. Handle Pipe Union: "A | B"
        val pipeParts = splitRespectingBrackets(result, '|')
        if (pipeParts.size > 1) {
            val filtered = pipeParts.map { it.trim() }.filter { it != "None" }
            if (filtered.isEmpty()) return "Any" // Should not happen for valid types
            if (filtered.size == 1) return removeNoneFromAnnotation(filtered[0])
            return filtered.joinToString(" | ")
        }

        // 2. Handle Union[...]
        if (result.startsWith("Union[") && result.endsWith("]")) {
            val content = result.substring("Union[".length, result.length - 1)
            val parts = splitRespectingBrackets(content, ',')
            val filtered = parts.map { it.trim() }.filter { it != "None" }
            if (filtered.isEmpty()) return "Any"
            if (filtered.size == 1) return removeNoneFromAnnotation(filtered[0])
            return "Union[${filtered.joinToString(", ")}]"
        }

        // 3. Handle Optional[...]
        if (result.startsWith("Optional[") && result.endsWith("]")) {
            val content = result.substring("Optional[".length, result.length - 1)
            return removeNoneFromAnnotation(content)
        }

        return result
    }

    private fun splitRespectingBrackets(text: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        var bracketCount = 0
        var currentPart = StringBuilder()

        for (char in text) {
            if (char == '[') bracketCount++
            else if (char == ']') bracketCount--

            if (char == delimiter && bracketCount == 0) {
                parts.add(currentPart.toString())
                currentPart = StringBuilder()
            } else {
                currentPart.append(char)
            }
        }
        parts.add(currentPart.toString())
        return parts
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
