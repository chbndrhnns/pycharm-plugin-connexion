package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

class PytestFixtureScopeInspection : PyInspection() {
    override fun getShortName(): String = "PytestFixtureScopeInspection"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR

        val settings = PluginSettingsState.instance().state
        if (!settings.enablePytestFixtureScopeInspection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : PyElementVisitor() {
            override fun visitPyFunction(node: PyFunction) {
                if (!PytestFixtureUtil.isFixtureFunction(node)) return

                val fixtureScope = PytestFixtureUtil.getFixtureScope(node) ?: return
                val context = TypeEvalContext.codeAnalysis(node.project, node.containingFile)

                for (parameter in node.parameterList.parameters) {
                    val name = parameter.name ?: continue
                    if (name == "self" || name == "cls") continue

                    val chain = PytestFixtureResolver.findFixtureChain(node, name, context)
                    val dependency = chain.firstOrNull()?.fixtureFunction ?: continue
                    val dependencyScope = PytestFixtureUtil.getFixtureScope(dependency) ?: continue

                    if (dependencyScope.order < fixtureScope.order) {
                        val message =
                            "Fixture dependency '$name' has narrower scope '${dependencyScope.value}' than fixture scope '${fixtureScope.value}'"
                        holder.registerProblem(
                            parameter,
                            message,
                            ApplyFixtureScopeToDependencyQuickFix(fixtureScope, name)
                        )
                    }
                }
            }
        }
    }

    private class ApplyFixtureScopeToDependencyQuickFix(
        private val scope: PytestFixtureUtil.PytestFixtureScope,
        private val dependencyName: String
    ) : LocalQuickFix {
        override fun getFamilyName(): String =
            PluginConstants.ACTION_PREFIX + "Apply fixture scope \"${scope.value}\" to fixture '$dependencyName'"

        override fun applyFix(project: com.intellij.openapi.project.Project, descriptor: ProblemDescriptor) {
            val function = PsiTreeUtil.getParentOfType(descriptor.psiElement, PyFunction::class.java) ?: return
            val context = TypeEvalContext.codeAnalysis(project, function.containingFile)
            val chain = PytestFixtureResolver.findFixtureChain(function, dependencyName, context)
            val dependency = chain.firstOrNull()?.fixtureFunction ?: return
            val decorator = PytestFixtureUtil.findFixtureDecorators(dependency).firstOrNull() ?: return
            applyScopeToDecorator(project, dependency, decorator, scope.value)
        }

        private fun applyScopeToDecorator(
            project: com.intellij.openapi.project.Project,
            function: PyFunction,
            decorator: PyDecorator,
            scopeValue: String
        ) {
            val generator = PyElementGenerator.getInstance(project)
            val languageLevel = LanguageLevel.forElement(function)
            val argumentList = decorator.argumentList

            if (argumentList == null) {
                val calleeText = decorator.callee?.text ?: decorator.name ?: "fixture"
                val newDecoratorList = generator.createDecoratorList("@$calleeText(scope=\"$scopeValue\")")
                val newDecorator = newDecoratorList.decorators.firstOrNull() ?: return
                decorator.replace(newDecorator)
                return
            }

            val scopeArg = argumentList.getKeywordArgument("scope")
            val newValueExpr =
                generator.createExpressionFromText(languageLevel, "\"$scopeValue\"") as? PyStringLiteralExpression
            if (scopeArg != null) {
                val valueExpr = scopeArg.valueExpression
                if (valueExpr != null && newValueExpr != null) {
                    valueExpr.replace(newValueExpr)
                } else {
                    val newKw = generator.createKeywordArgument(languageLevel, "scope", "\"$scopeValue\"")
                    scopeArg.replace(newKw)
                }
                return
            }

            val newKw = generator.createKeywordArgument(languageLevel, "scope", "\"$scopeValue\"")
            argumentList.addArgument(newKw)
        }
    }
}
