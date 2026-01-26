package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInspection.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.TypeEvalContext

class PytestFixtureUninjectedReferenceInspection : PyInspection() {
    private val log = Logger.getInstance(PytestFixtureUninjectedReferenceInspection::class.java)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR

        val settings = PluginSettingsState.instance().state
        if (!settings.enablePytestFixtureUninjectedReferenceInspection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        val context = TypeEvalContext.codeAnalysis(holder.project, holder.file)

        return object : PyElementVisitor() {
            override fun visitPyReferenceExpression(node: PyReferenceExpression) {
                super.visitPyReferenceExpression(node)

                if (node.qualifier != null) return
                val name = node.name ?: return
                if (name == "self" || name == "cls") return

                val function = PsiTreeUtil.getParentOfType(node, PyFunction::class.java) ?: return
                if (!isTestOrFixtureFunction(function)) return
                val statementList = function.statementList
                if (!PsiTreeUtil.isAncestor(statementList, node, false)) return

                if (function.parameterList.parameters.any { it.name == name }) return

                val resolved = node.reference?.resolve()
                if (resolved != null) {
                    val resolvedFunction = resolved as? PyFunction ?: return
                    if (!PytestFixtureUtil.isFixtureFunction(resolvedFunction)) return
                }

                val chain = PytestFixtureResolver.findFixtureChain(node, name, context)
                if (chain.isEmpty()) return

                val nameElement = node.nameElement?.psi ?: node
                if (log.isDebugEnabled) {
                    log.debug("PytestFixtureUninjectedReferenceInspection: '$name' used without injection in ${function.name}")
                }
                holder.registerProblem(
                    nameElement,
                    "Fixture '$name' is not injected",
                    ProblemHighlightType.ERROR,
                    InjectFixtureParameterQuickFix(name)
                )
            }
        }
    }

    private fun isTestOrFixtureFunction(function: PyFunction): Boolean {
        if (PytestNaming.isTestFunction(function)) {
            return true
        }
        return PytestFixtureUtil.isFixtureFunction(function)
    }

    private class InjectFixtureParameterQuickFix(private val fixtureName: String) : LocalQuickFix {
        override fun getFamilyName(): String =
            PluginConstants.ACTION_PREFIX + "Inject pytest fixture '$fixtureName'"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val function = PsiTreeUtil.getParentOfType(descriptor.psiElement, PyFunction::class.java) ?: return
            val parameterList = function.parameterList
            if (parameterList.parameters.any { it.name == fixtureName }) return

            val generator = PyElementGenerator.getInstance(project)
            val newParam = generator.createParameter(fixtureName)
            parameterList.addParameter(newParam)
        }
    }
}
