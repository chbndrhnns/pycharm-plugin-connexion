package com.github.chbndrhnns.betterpy.features.pytest.parametrize

import com.github.chbndrhnns.betterpy.core.pytest.PytestParametrizeUtil
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*

class PytestParametrizeCountMismatchInspection : PyInspection() {
    override fun getShortName(): String = "PytestParametrizeCountMismatchInspection"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR

        val settings = PluginSettingsState.instance().state
        if (!settings.enablePytestParametrizeCountMismatchInspection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : PyElementVisitor() {
            override fun visitPyDecorator(node: PyDecorator) {
                if (!PytestParametrizeUtil.isParametrizeDecorator(node, allowBareName = true)) return

                val args = node.argumentList?.arguments ?: return
                if (args.size < 2) return

                val namesArg = if (args[0] !is PyKeywordArgument) {
                    args[0]
                } else {
                    node.getKeywordArgument("argnames") ?: return
                }

                val valuesArg = if (args[1] !is PyKeywordArgument) {
                    args[1]
                } else {
                    node.getKeywordArgument("argvalues") ?: return
                }

                val paramNames = PytestParametrizeUtil.extractParameterNames(namesArg) ?: return
                val paramCount = paramNames.size

                // Single parameter: values are flat, no tuple needed — skip check
                if (paramCount <= 1) return

                val valuesList = when (valuesArg) {
                    is PyListLiteralExpression -> valuesArg.elements.toList()
                    is PyTupleExpression -> valuesArg.elements.toList()
                    else -> return
                }

                for (valueElement in valuesList) {
                    val actualCount = countValues(valueElement) ?: continue
                    if (actualCount != paramCount) {
                        holder.registerProblem(
                            valueElement,
                            "Expected $paramCount values per parameter set (${paramNames.joinToString(", ")}), but got $actualCount"
                        )
                    }
                }

                // Check ids length matches argvalues length
                checkIdsLength(node, valuesList.size, holder)
            }
        }
    }

    private fun checkIdsLength(decorator: PyDecorator, argvaluesCount: Int, holder: ProblemsHolder) {
        val idsValue = decorator.getKeywordArgument("ids") ?: return

        val idsCount = when (idsValue) {
            is PyListLiteralExpression -> idsValue.elements.size
            is PyTupleExpression -> idsValue.elements.size
            else -> return
        }

        if (idsCount != argvaluesCount) {
            holder.registerProblem(
                idsValue,
                "Expected $argvaluesCount ids (one per parameter set), but got $idsCount"
            )
        }
    }

    private fun countValues(element: PyExpression): Int? {
        return when (element) {
            is PyTupleExpression -> element.elements.size
            is PyParenthesizedExpression -> {
                val contained = element.containedExpression
                if (contained is PyTupleExpression) contained.elements.size else null
            }
            is PyListLiteralExpression -> element.elements.size
            is PyCallExpression -> {
                // pytest.param(...)
                if (PytestParametrizeUtil.isPytestParamCall(element)) {
                    element.arguments.count { it !is PyKeywordArgument }
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
