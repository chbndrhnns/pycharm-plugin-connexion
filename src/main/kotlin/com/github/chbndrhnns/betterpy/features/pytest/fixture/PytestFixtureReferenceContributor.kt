package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

private val LOG = Logger.getInstance(PytestFixtureReferenceContributor::class.java)

/**
 * Provides references for pytest fixture usages:
 * - Fixture parameters in test/fixture functions
 * - String literals in @pytest.mark.usefixtures("...")
 */
class PytestFixtureReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // Register for named parameters (fixture parameters)
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PyNamedParameter::class.java),
            FixtureParameterReferenceProvider()
        )

        // Register for string literals (usefixtures)
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PyStringLiteralExpression::class.java),
            UsefixturesStringReferenceProvider()
        )
    }
}

/**
 * Provides references for fixture parameters in test/fixture functions.
 */
class FixtureParameterReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext
    ): Array<PsiReference> {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return PsiReference.EMPTY_ARRAY
        }
        val parameter = element as? PyNamedParameter ?: return PsiReference.EMPTY_ARRAY

        // Check if this parameter is in a test or fixture function
        val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java)
            ?: return PsiReference.EMPTY_ARRAY
        if (!isTestOrFixtureFunction(function)) {
            return PsiReference.EMPTY_ARRAY
        }

        // Don't provide references for 'self' or 'cls'
        val paramName = parameter.name ?: return PsiReference.EMPTY_ARRAY
        if (paramName == "self" || paramName == "cls") {
            return PsiReference.EMPTY_ARRAY
        }

        if (LOG.isDebugEnabled) {
            LOG.debug(
                "FixtureParameterReferenceProvider: providing fixture reference for '$paramName' in function " +
                        "'${function.name ?: "<anonymous>"}'"
            )
        }
        return arrayOf(PytestFixtureReference(parameter, paramName))
    }

    private fun isTestOrFixtureFunction(function: PyFunction): Boolean {
        if (PytestNaming.isTestFunction(function)) {
            return true
        }
        return PytestFixtureUtil.isFixtureFunction(function)
    }
}

/**
 * Provides references for string literals in @pytest.mark.usefixtures("...").
 */
class UsefixturesStringReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext
    ): Array<PsiReference> {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return PsiReference.EMPTY_ARRAY
        }
        val literal = element as? PyStringLiteralExpression ?: return PsiReference.EMPTY_ARRAY

        if (!isUsefixturesArgument(literal)) {
            return PsiReference.EMPTY_ARRAY
        }

        val fixtureName = literal.stringValue
        if (fixtureName.isEmpty()) {
            return PsiReference.EMPTY_ARRAY
        }

        // Create a reference for the entire string value
        val valueRange = literal.stringValueTextRange
        val endOffset = minOf(valueRange.endOffset + 1, literal.textLength)
        val range = TextRange(valueRange.startOffset, endOffset)
        if (LOG.isDebugEnabled) {
            LOG.debug("UsefixturesStringReferenceProvider: providing fixture reference for '$fixtureName'")
        }
        return arrayOf(PytestFixtureStringReference(literal, range, fixtureName))
    }

    private fun isUsefixturesArgument(literal: PyStringLiteralExpression): Boolean {
        // Check if this string is an argument to @pytest.mark.usefixtures
        val argumentList = literal.parent as? PyArgumentList ?: return false
        if (argumentList.parent !is PyCallExpression) return false

        val decorator = PsiTreeUtil.getParentOfType(literal, PyDecorator::class.java) ?: return false
        val qName = decorator.qualifiedName?.toString() ?: return false

        return qName == "pytest.mark.usefixtures" ||
                qName == "_pytest.mark.usefixtures" ||
                qName == "mark.usefixtures" ||
                qName.endsWith(".mark.usefixtures")
    }
}

/**
 * Reference from a fixture parameter to fixture definition(s).
 */
class PytestFixtureReference(
    element: PyNamedParameter,
    private val fixtureName: String
) : PsiReferenceBase<PyNamedParameter>(element, TextRange(0, element.textLength), false),
    PsiPolyVariantReference,
    LocalQuickFixProvider {

    override fun resolve(): PsiElement? {
        val results = multiResolve(false)
        return if (results.isNotEmpty()) results[0].element else null
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return ResolveResult.EMPTY_ARRAY
        }
        val context = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
        val chain = PytestFixtureResolver.findFixtureChain(element, fixtureName, context)
        if (LOG.isDebugEnabled) {
            LOG.debug("PytestFixtureReference: resolved '${fixtureName}' to ${chain.size} fixture(s)")
        }

        return chain.map { link ->
            PsiElementResolveResult(link.fixtureFunction)
        }.toTypedArray()
    }

    override fun getVariants(): Array<Any> {
        // Could provide completion variants here
        return emptyArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        return element.setName(newElementName)
    }

    override fun getQuickFixes(): Array<LocalQuickFix> {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return emptyArray()
        }
        if (!PluginSettingsState.instance().state.enableCreatePytestFixtureFromParameter) {
            return emptyArray()
        }
        if (multiResolve(false).isNotEmpty()) {
            return emptyArray()
        }
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return emptyArray()
        if (!PytestFixtureUtil.isFixtureFunction(function)) {
            return emptyArray()
        }
        return arrayOf(CreateFixtureFromParameterQuickFix(fixtureName))
    }
}

/**
 * Reference from a usefixtures string to fixture definition(s).
 */
class PytestFixtureStringReference(
    element: PyStringLiteralExpression,
    rangeInElement: TextRange,
    private val fixtureName: String
) : PsiReferenceBase<PyStringLiteralExpression>(element, rangeInElement, false), PsiPolyVariantReference {

    override fun resolve(): PsiElement? {
        val results = multiResolve(false)
        return if (results.isNotEmpty()) results[0].element else null
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return ResolveResult.EMPTY_ARRAY
        }
        val context = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
        val chain = PytestFixtureResolver.findFixtureChain(element, fixtureName, context)
        if (LOG.isDebugEnabled) {
            LOG.debug("PytestFixtureStringReference: resolved '${fixtureName}' to ${chain.size} fixture(s)")
        }

        return chain.map { link ->
            PsiElementResolveResult(link.fixtureFunction)
        }.toTypedArray()
    }

    override fun getVariants(): Array<Any> {
        // Could provide completion variants here
        return emptyArray()
    }
}
