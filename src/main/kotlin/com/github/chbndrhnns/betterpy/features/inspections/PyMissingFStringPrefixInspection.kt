package com.github.chbndrhnns.betterpy.features.inspections

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.github.chbndrhnns.betterpy.features.intentions.FStringConversionUtil
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyStringLiteralExpression

class PyMissingFStringPrefixInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        val settings = PluginSettingsState.instance().state
        if (!settings.enableMissingFStringPrefixInspection) {
            return object : PyElementVisitor() {}
        }

        return object : PyElementVisitor() {
            override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
                val prefix = FStringConversionUtil.parsePrefix(node.text) ?: return
                if (FStringConversionUtil.shouldSkip(prefix)) return
                if (!FStringConversionUtil.containsFStringPlaceholder(node.stringValue)) return

                holder.registerProblem(
                    node,
                    "String contains interpolation-like placeholders but is missing the 'f' prefix",
                    ConvertToFStringQuickFix()
                )
            }
        }
    }

    private class ConvertToFStringQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = PluginConstants.ACTION_PREFIX + "Convert to f-string"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val literal = descriptor.psiElement as? PyStringLiteralExpression ?: return
            FStringConversionUtil.convertToFString(literal)
        }
    }
}
