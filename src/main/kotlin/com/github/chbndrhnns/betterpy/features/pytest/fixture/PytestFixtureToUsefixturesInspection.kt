package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*

class PytestFixtureToUsefixturesInspection : PyInspection() {
    override fun getShortName(): String = "PytestFixtureToUsefixturesInspection"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR

        val settings = PluginSettingsState.instance().state
        if (!settings.enablePytestFixtureToUsefixturesInspection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : PyElementVisitor() {
            override fun visitPyFunction(node: PyFunction) {
                if (!isTestFunction(node)) return

                val statementList = node.statementList
                val parametrizeNames = getParametrizeArgNames(node)
                for (parameter in node.parameterList.parameters) {
                    val name = parameter.name ?: continue
                    if (name == "self" || name == "cls" || name == "request") continue
                    if (name in parametrizeNames) continue

                    if (!isParameterUsedInBody(name, statementList)) {
                        val message = "Fixture '$name' is not used in test body, convert to @pytest.mark.usefixtures()"
                        holder.registerProblem(
                            parameter,
                            message,
                            ConvertToUsefixturesQuickFix(name)
                        )
                    }
                }
            }
        }
    }

    private fun isTestFunction(function: PyFunction): Boolean {
        val name = function.name ?: return false
        if (!name.startsWith("test_") && !name.startsWith("test")) return false
        // Exclude fixture functions
        if (PytestFixtureUtil.isFixtureFunction(function)) return false
        return true
    }

    private fun getParametrizeArgNames(function: PyFunction): Set<String> {
        val decorators = function.decoratorList?.decorators ?: return emptySet()
        val names = mutableSetOf<String>()
        for (decorator in decorators) {
            val qualifiedName =
                decorator.callee?.let { (it as? PyQualifiedExpression)?.asQualifiedName()?.toString() }
            if (qualifiedName != "pytest.mark.parametrize") continue
            val args = decorator.argumentList?.arguments ?: continue
            val firstArg = args.firstOrNull() ?: continue
            when (firstArg) {
                is PyStringLiteralExpression -> firstArg.stringValue.split(",").forEach { names.add(it.trim()) }
                is PyTupleExpression -> firstArg.elements.filterIsInstance<PyStringLiteralExpression>().forEach { names.add(it.stringValue.trim()) }
                is PyListLiteralExpression -> firstArg.elements.filterIsInstance<PyStringLiteralExpression>().forEach { names.add(it.stringValue.trim()) }
                is PyParenthesizedExpression -> {
                    val contained = firstArg.containedExpression
                    if (contained is PyTupleExpression) {
                        contained.elements.filterIsInstance<PyStringLiteralExpression>().forEach { names.add(it.stringValue.trim()) }
                    }
                }
            }
        }
        return names
    }

    private fun isParameterUsedInBody(paramName: String, statementList: PyStatementList): Boolean {
        val references = PsiTreeUtil.findChildrenOfType(statementList, PyReferenceExpression::class.java)
        return references.any { it.name == paramName }
    }

    private class ConvertToUsefixturesQuickFix(
        private val fixtureName: String
    ) : LocalQuickFix {
        override fun getFamilyName(): String =
            PluginConstants.ACTION_PREFIX + "Convert to @pytest.mark.usefixtures()"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val parameter = descriptor.psiElement as? PyNamedParameter ?: return
            val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return
            val generator = PyElementGenerator.getInstance(project)
            LanguageLevel.forElement(function)

            // Remove the parameter from the function signature
            parameter.delete()

            // Clean up trailing/leading commas in parameter list
            cleanupParameterList(function.parameterList)

            // Add to existing @pytest.mark.usefixtures or create new decorator
            val existingUsefixtures = findUsefixturesDecorator(function)
            if (existingUsefixtures != null) {
                appendToUsefixtures(existingUsefixtures, fixtureName, generator)
            } else {
                addUsefixturesDecorator(function, fixtureName, generator)
            }
        }

        private fun cleanupParameterList(parameterList: PyParameterList) {
            // Remove any stray commas that may remain after parameter deletion
            for (child in parameterList.children) {
                if (child.text.trim() == ",") {
                    child.delete()
                }
            }
        }

        private fun findUsefixturesDecorator(function: PyFunction): PyDecorator? {
            val decorators = function.decoratorList?.decorators ?: return null
            return decorators.firstOrNull { decorator ->
                val qualifiedName =
                    decorator.callee?.let { (it as? PyQualifiedExpression)?.asQualifiedName()?.toString() }
                qualifiedName == "pytest.mark.usefixtures"
            }
        }

        private fun appendToUsefixtures(
            decorator: PyDecorator,
            fixtureName: String,
            generator: PyElementGenerator
        ) {
            val argumentList = decorator.argumentList ?: return
            val newArg = generator.createStringLiteralAlreadyEscaped("\"$fixtureName\"")
            argumentList.addArgument(newArg)
        }

        private fun addUsefixturesDecorator(
            function: PyFunction,
            fixtureName: String,
            @Suppress("UNUSED_PARAMETER") generator: PyElementGenerator
        ) {
            val decoratorText = """@pytest.mark.usefixtures("$fixtureName")"""
            PyUtil.addDecorator(function, decoratorText)
        }
    }
}
