package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.github.chbndrhnns.betterpy.core.pytest.PytestParametrizeUtil
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

        // Register for string literals in @pytest.mark.parametrize (parameter name references)
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PyStringLiteralExpression::class.java),
            ParametrizeNameReferenceProvider()
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

        // Check if this parameter belongs to a lambda - if so, skip it
        val parameterList = parameter.parent as? PyParameterList
        if (parameterList?.parent is PyLambdaExpression) {
            return PsiReference.EMPTY_ARRAY
        }

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

        // Don't provide fixture references for parametrize parameters
        if (paramName in PytestParametrizeUtil.collectAllParametrizeNames(function)) {
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
) : PsiPolyVariantReferenceBase<PyNamedParameter>(element, TextRange(0, element.textLength), false),
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

        // When a fixture parameter has the same name as its containing fixture (override pattern),
        // filter out the containing function so that navigation goes to the parent fixture.
        val containingFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        val filtered = if (containingFunction != null && PytestFixtureUtil.isFixtureFunction(containingFunction)) {
            chain.filter { it.fixtureFunction !== containingFunction }
        } else {
            chain
        }

        if (LOG.isDebugEnabled) {
            val fileName = element.containingFile?.virtualFile?.path ?: "<unknown>"
            LOG.debug(
                "PytestFixtureReference: fixtureName='$fixtureName', file='$fileName', resolved=${filtered.size}"
            )
        }

        return filtered.map { link ->
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
 * Provides references from parametrize name strings to function parameters.
 * E.g. @pytest.mark.parametrize("param_name", [...]) -> function parameter param_name.
 */
class ParametrizeNameReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext
    ): Array<PsiReference> {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return PsiReference.EMPTY_ARRAY
        }
        val literal = element as? PyStringLiteralExpression ?: return PsiReference.EMPTY_ARRAY

        val decorator = PsiTreeUtil.getParentOfType(literal, PyDecorator::class.java)
            ?: return PsiReference.EMPTY_ARRAY
        if (!PytestParametrizeUtil.isParametrizeDecorator(decorator, allowBareName = true)) {
            return PsiReference.EMPTY_ARRAY
        }

        // Check this is the argnames argument (first positional or argnames= keyword)
        if (!isArgnamesArgument(literal, decorator)) {
            return PsiReference.EMPTY_ARRAY
        }

        val function = PsiTreeUtil.getParentOfType(decorator, PyFunction::class.java)
            ?: return PsiReference.EMPTY_ARRAY

        val stringValue = literal.stringValue
        if (stringValue.isEmpty()) return PsiReference.EMPTY_ARRAY

        // Split comma-delimited names and create a reference for each
        val names = stringValue.split(',').map { it.trim() }
        val references = mutableListOf<PsiReference>()
        val valueRange = literal.stringValueTextRange
        var searchFrom = valueRange.startOffset

        for (name in names) {
            if (name.isEmpty()) continue
            val param = function.parameterList.findParameterByName(name) ?: continue

            // Find the offset of this name within the string element text
            val nameOffset = literal.text.indexOf(name, searchFrom)
            if (nameOffset < 0) continue
            val range = TextRange(nameOffset, nameOffset + name.length)
            searchFrom = nameOffset + name.length

            references.add(ParametrizeNameReference(literal, range, name, param))
        }

        return references.toTypedArray()
    }

    private fun isArgnamesArgument(literal: PyStringLiteralExpression, decorator: PyDecorator): Boolean {
        // Check if it's the argnames= keyword argument
        val keywordArg = literal.parent as? PyKeywordArgument
        if (keywordArg != null) {
            return keywordArg.keyword == "argnames"
        }
        // Check if it's the first positional argument
        val argumentList = literal.parent as? PyArgumentList
        if (argumentList != null) {
            val args = argumentList.arguments
            return args.isNotEmpty() && args[0] === literal
        }
        // Check if it's inside a tuple/list/parenthesized expr that is the first positional argument
        val container = literal.parent ?: return false
        val firstArgCandidate: PsiElement = when (container) {
            is com.jetbrains.python.psi.PyTupleExpression,
            is com.jetbrains.python.psi.PyListLiteralExpression -> {
                // Tuple/list may be directly in arglist, or wrapped in PyParenthesizedExpression
                val upper = container.parent
                if (upper is com.jetbrains.python.psi.PyParenthesizedExpression) upper else container
            }

            is com.jetbrains.python.psi.PyParenthesizedExpression -> container
            else -> return false
        }
        val argList = firstArgCandidate.parent as? PyArgumentList ?: return false
        val args = argList.arguments
        return args.isNotEmpty() && args[0] === firstArgCandidate
    }
}

/**
 * Hard reference from a parametrize name string to a function parameter.
 */
class ParametrizeNameReference(
    element: PyStringLiteralExpression,
    rangeInElement: TextRange,
    private val paramName: String,
    private val target: PyNamedParameter
) : PsiReferenceBase<PyStringLiteralExpression>(element, rangeInElement, false) {

    override fun resolve(): PsiElement = target

    override fun handleElementRename(newElementName: String): PsiElement {
        val oldText = element.text
        val rangeInText = rangeInElement
        val newText = oldText.substring(0, rangeInText.startOffset) +
                newElementName +
                oldText.substring(rangeInText.endOffset)
        val generator = PyElementGenerator.getInstance(element.project)
        val newLiteral = generator.createStringLiteralAlreadyEscaped(newText)
        return element.replace(newLiteral)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}

/**
 * Reference from a usefixtures string to fixture definition(s).
 */
class PytestFixtureStringReference(
    element: PyStringLiteralExpression,
    rangeInElement: TextRange,
    private val fixtureName: String
) : PsiPolyVariantReferenceBase<PyStringLiteralExpression>(element, rangeInElement, false) {

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
            val fileName = element.containingFile?.virtualFile?.path ?: "<unknown>"
            LOG.debug(
                "PytestFixtureStringReference: fixtureName='$fixtureName', file='$fileName', resolved=${chain.size}"
            )
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
