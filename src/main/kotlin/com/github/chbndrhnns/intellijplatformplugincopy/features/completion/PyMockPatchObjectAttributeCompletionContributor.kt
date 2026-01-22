package com.github.chbndrhnns.intellijplatformplugincopy.features.completion

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Provides completion suggestions for the attribute argument in `patch.object(target, "attribute")` calls.
 */
class PyMockPatchObjectAttributeCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            psiElement().withLanguage(PythonLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    if (!PluginSettingsState.instance().state.enableMockPatchObjectAttributeCompletionContributor) return

                    val position = parameters.position
                    val stringLiteral = position.parent as? PyStringLiteralExpression ?: return
                    val argumentList = stringLiteral.parent as? PyArgumentList ?: return
                    val call = argumentList.parent as? PyCallExpression ?: return

                    if (!isPatchObjectCall(call)) return

                    val arguments = argumentList.arguments
                    if (arguments.size < 2 || arguments[1] !== stringLiteral) return

                    val targetArg = arguments[0]
                    val typeContext = TypeEvalContext.codeAnalysis(position.project, position.containingFile)
                    val targetType = typeContext.getType(targetArg) ?: return

                    // Get all members from the target type
                    val members = when (targetType) {
                        is PyClassType -> targetType.pyClass.classAttributes +
                                targetType.pyClass.methods.mapNotNull { it.name }

                        else -> targetType.resolveMember(
                            "",
                            null,
                            AccessDirection.READ,
                            com.jetbrains.python.psi.resolve.PyResolveContext.defaultContext(typeContext)
                        )?.mapNotNull { it.element?.let { el -> (el as? PyElement)?.name } } ?: emptyList()
                    }

                    val memberNames = when (targetType) {
                        is PyClassType -> {
                            val pyClass = targetType.pyClass
                            val methods = pyClass.methods.mapNotNull { it.name }
                                .filter { !it.startsWith("__") || it.endsWith("__") && it != "__init__" }
                            val attrs = pyClass.classAttributes.mapNotNull { it.name }
                            val instanceAttrs = pyClass.instanceAttributes.mapNotNull { it.name }
                            (methods + attrs + instanceAttrs).distinct()
                        }

                        else -> emptyList()
                    }

                    memberNames.forEach { name ->
                        val element = LookupElementBuilder.create(name)
                            .withTypeText(targetType.name ?: "")
                        val prioritized = PrioritizedLookupElement.withPriority(element, 100.0)
                        result.addElement(prioritized)
                    }
                }
            }
        )
    }

    private fun isPatchObjectCall(call: PyCallExpression): Boolean {
        val callee = call.callee as? PyReferenceExpression ?: return false
        if (callee.name == "object") {
            val qualifier = callee.qualifier as? PyReferenceExpression
            if (qualifier?.name == "patch") {
                return true
            }
        }
        return false
    }
}
