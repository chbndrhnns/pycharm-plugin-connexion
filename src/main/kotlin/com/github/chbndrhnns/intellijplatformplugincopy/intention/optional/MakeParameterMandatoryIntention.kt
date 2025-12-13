package com.github.chbndrhnns.intellijplatformplugincopy.intention.optional

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.mapArguments
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.changeSignature.PyChangeSignatureProcessor
import com.jetbrains.python.refactoring.changeSignature.PyMethodDescriptor

class MakeParameterMandatoryIntention : IntentionAction, PriorityAction {

    override fun getText(): String = "Make mandatory"

    override fun getFamilyName(): String = "Make mandatory"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableMakeParameterMandatoryIntention) return false
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

        // Ensure element is PyAnnotationOwner before updating annotation
        if (element !is PyAnnotationOwner) return

        if (element is PyNamedParameter) {
            val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return
            val problematicCallSites = findCallSitesWithMissingArgument(element, function)

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

                // Update call sites manually to ensure keyword argument usage
                val generator = PyElementGenerator.getInstance(project)
                val elementName = element.name ?: return

                for (call in problematicCallSites) {
                    val argumentList = call.argumentList ?: continue
                    // We add it as a keyword argument: arg=...
                    val keywordArg = generator.createKeywordArgument(
                        LanguageLevel.forElement(call),
                        elementName,
                        "..."
                    )
                    argumentList.addArgument(keywordArg)
                }
            }

            // 1. Update Annotation (Remove 'None')
            updateAnnotation(element, project)

            // 2. Update Definition (Remove default value) using Change Signature
            // Use PyMethodDescriptor to get current parameters
            val descriptor = PyMethodDescriptor(function)
            val parameters = descriptor.parameters

            // Modify the target parameter info
            val paramInfo = parameters.find { it.name == element.name }
            val functionName = function.name
            if (paramInfo != null && functionName != null) {
                paramInfo.defaultValue = "..." // Value to add to missing call sites (if any left)
                paramInfo.defaultInSignature = false // Remove default value from definition

                // Execute Change Signature Refactoring
                val processor =
                    PyChangeSignatureProcessor(project, function, functionName, parameters.toTypedArray())
                processor.run()
            }
        } else if (element is PyTargetExpression) {
            val parent = element.parent
            val generator = PyElementGenerator.getInstance(project)

            // 1. Update Annotation
            updateAnnotation(element, project)

            // 2. Update Assignment or Type Declaration
            // Get the updated annotation text. Use value.text to retrieve just the type expression (e.g., "int").
            val annotationText = element.annotation?.value?.text ?: "Any"
            val newStatementText = "${element.name}: $annotationText"

            if (parent is PyAssignmentStatement) {
                val newStatement = generator.createFromText(
                    LanguageLevel.forElement(element),
                    PyTypeDeclarationStatement::class.java,
                    newStatementText
                )
                parent.replace(newStatement)
            } else if (parent is PyTypeDeclarationStatement) {
                val newStatement = generator.createFromText(
                    LanguageLevel.forElement(element),
                    PyTypeDeclarationStatement::class.java,
                    newStatementText
                )
                parent.replace(newStatement)
            }
        }
    }

    private fun findCallSitesWithMissingArgument(
        element: PyNamedParameter,
        function: PyFunction
    ): List<PyCallExpression> {
        val references = ReferencesSearch.search(function, function.useScope).findAll()
        val missingSites = mutableListOf<PyCallExpression>()

        for (ref in references) {
            val callElement = ref.element.parent
            if (callElement is PyCallExpression) {
                // Use codeInsightFallback to avoid heavy inference but still resolve arguments
                val context = TypeEvalContext.codeInsightFallback(element.project)
                // Use extension function mapArguments imported from com.jetbrains.python.psi.impl
                val mapping = callElement.mapArguments(function, context)

                // Check if element is mapped in the arguments
                val mapped = mapping.mappedParameters.values.any { it.parameter == element }
                if (!mapped) {
                    missingSites.add(callElement)
                }
            }
        }
        return missingSites
    }

    private fun updateAnnotation(element: PyAnnotationOwner, project: Project) {
        val annotation = element.annotation ?: return
        val value = annotation.value ?: return
        val newAnnotationText = processTypeExpression(value) ?: "Any"

        if (newAnnotationText != value.text) {
            val generator = PyElementGenerator.getInstance(project)
            val newExpression = generator.createExpressionFromText(LanguageLevel.forElement(element), newAnnotationText)
            value.replace(newExpression)
        }
    }

    private fun processTypeExpression(expression: PyExpression): String? {
        // Handle None literal (PyNoneLiteralExpression or reference "None")
        if (expression is PyNoneLiteralExpression) return null
        if (expression.text == "None") return null

        when (expression) {
            is PyBinaryExpression -> {
                if (expression.operator == PyTokenTypes.OR) { // Type | None
                    val left = processTypeExpression(expression.leftExpression)
                    val right = expression.rightExpression?.let { processTypeExpression(it) }
                    return joinTypes(listOfNotNull(left, right), " | ")
                }
            }

            is PySubscriptionExpression -> {
                val operand = expression.operand
                val index = expression.indexExpression ?: return expression.text

                // Resolve "Optional" and "Union" using TypeEvalContext to handle imports/aliases correctly
                val context = TypeEvalContext.userInitiated(expression.project, expression.containingFile)

                val qualifiedNames = PyTypingTypeProvider.resolveToQualifiedNames(operand, context)

                if (qualifiedNames.any { it == "typing.Optional" || it == "Optional" }) {
                    // Optional[T] -> T
                    return processTypeExpression(index)
                }

                if (qualifiedNames.any { it == "typing.Union" || it == "Union" }) {
                    // Union[A, B, None] -> Union[A, B]
                    val elements = if (index is PyTupleExpression) index.elements.toList() else listOf(index)
                    val processed = elements.mapNotNull { processTypeExpression(it) }
                    return if (processed.size == 1) processed[0] else "Union[${processed.joinToString(", ")}]"
                }
            }

            is PyReferenceExpression -> {
                if (expression.text == "None") return null
            }
        }

        // Default: keep the text if it's not None
        return if (expression.text == "None") null else expression.text
    }

    private fun joinTypes(types: List<String>, separator: String): String? {
        if (types.isEmpty()) return null
        if (types.size == 1) return types[0]
        return types.joinToString(separator)
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
            if (PsiTreeUtil.getParentOfType(target, PyClass::class.java) != null) {
                return target
            }
        }
        return null
    }
}