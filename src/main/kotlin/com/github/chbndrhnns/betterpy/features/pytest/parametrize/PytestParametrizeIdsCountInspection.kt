package com.github.chbndrhnns.betterpy.features.pytest.parametrize

import com.github.chbndrhnns.betterpy.core.pytest.PytestParametrizeUtil
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*

class PytestParametrizeIdsCountInspection : PyInspection() {
    override fun getShortName(): String = "PytestParametrizeIdsCountInspection"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR

        val settings = PluginSettingsState.instance().state
        if (!settings.enablePytestParametrizeIdsCountInspection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : PyElementVisitor() {
            override fun visitPyDecorator(node: PyDecorator) {
                if (!PytestParametrizeUtil.isParametrizeDecorator(node, allowBareName = true)) return

                val args = node.argumentList?.arguments ?: return
                if (args.size < 2) return

                val idsExpr = node.getKeywordArgument("ids") ?: return

                val valuesArg = if (args[1] !is PyKeywordArgument) {
                    args[1]
                } else {
                    node.getKeywordArgument("argvalues") ?: return
                }

                val argvaluesCount = countArgvalues(valuesArg) ?: return

                // Resolve ids: either literal or follow a reference
                val resolvedIds = resolveToCollection(idsExpr)
                if (resolvedIds == null) {
                    // Dynamically calculated (lambda, function call, etc.) — skip
                    return
                }

                val idsCount = countCollectionElements(resolvedIds)
                if (idsCount == null) return

                if (idsCount != argvaluesCount) {
                    holder.registerProblem(
                        idsExpr,
                        "Expected $argvaluesCount ids (one per parameter set), but got $idsCount"
                    )
                }
            }
        }
    }

    private fun countArgvalues(valuesArg: PyExpression): Int? {
        return when (valuesArg) {
            is PyListLiteralExpression -> valuesArg.elements.size
            is PyTupleExpression -> valuesArg.elements.size
            else -> null
        }
    }

    private fun resolveToCollection(expr: PyExpression): PyExpression? {
        return when (expr) {
            is PyListLiteralExpression, is PyTupleExpression -> expr
            is PyParenthesizedExpression -> {
                val contained = expr.containedExpression
                when (contained) {
                    is PyTupleExpression -> contained
                    else -> null
                }
            }
            is PyReferenceExpression -> {
                val resolved = expr.reference.resolve()
                val target = resolved as? PyTargetExpression ?: return null
                val value = target.findAssignedValue() ?: return null
                when (value) {
                    is PyListLiteralExpression, is PyTupleExpression -> value
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun countCollectionElements(expr: PyExpression): Int? {
        return when (expr) {
            is PyListLiteralExpression -> expr.elements.size
            is PyTupleExpression -> expr.elements.size
            else -> null
        }
    }
}
