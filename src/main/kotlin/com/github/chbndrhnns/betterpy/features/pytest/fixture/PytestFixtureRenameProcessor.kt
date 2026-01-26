package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

class PytestFixtureRenameProcessor : RenamePsiElementProcessor() {
    private val log = Logger.getInstance(PytestFixtureRenameProcessor::class.java)

    override fun canProcessElement(element: PsiElement): Boolean {
        if (!PytestFixtureFeatureToggle.isEnabled()) return false
        return when (element) {
            is PyFunction -> PytestFixtureUtil.isFixtureFunction(element)
            is PyNamedParameter -> isFixtureParameter(element)
            is PyStringLiteralExpression -> isUsefixturesArgument(element)
            else -> false
        }
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        if (!PytestFixtureFeatureToggle.isEnabled()) return

        val fixtureName = fixtureNameForRename(element) ?: return
        if (element is PyFunction && element.name != fixtureName) return

        val related = ReadAction.compute<RelatedRenameTargets, RuntimeException> {
            val context = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
            val related = collectRelatedFixtures(element, fixtureName, context)
            val fixtures = related.filter { it.name == fixtureName }
            val parameters = collectFixtureParameters(element.project, fixtureName, fixtures)
            RelatedRenameTargets(fixtures, parameters.toList())
        }

        if (related.fixtures.isEmpty()) return
        if (log.isDebugEnabled) {
            log.debug("PytestFixtureRenameProcessor: renaming ${related.fixtures.size} related fixture(s) for '$fixtureName'")
        }

        for (fixture in related.fixtures) {
            allRenames[fixture] = newName
        }
        for (parameter in related.parameters) {
            allRenames[parameter] = newName
        }
    }

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference> {
        val references = delegateProcessor(element)
            ?.findReferences(element, searchScope, searchInCommentsAndStrings)
            ?: super.findReferences(element, searchScope, searchInCommentsAndStrings)
        if (element !is PyFunction || !PytestFixtureUtil.isFixtureFunction(element)) {
            return references
        }
        return references.filterNot { isFixtureDecoratorReference(it) }
    }

    override fun isToSearchForTextOccurrences(element: PsiElement): Boolean {
        if (element is PyFunction && PytestFixtureUtil.isFixtureFunction(element)) {
            return false
        }
        return delegateProcessor(element)?.isToSearchForTextOccurrences(element)
            ?: super.isToSearchForTextOccurrences(element)
    }

    override fun getTextOccurrenceSearchStrings(element: PsiElement, newName: String): Pair<String, String>? {
        if (element is PyFunction && PytestFixtureUtil.isFixtureFunction(element)) {
            return null
        }
        return delegateProcessor(element)?.getTextOccurrenceSearchStrings(element, newName)
            ?: super.getTextOccurrenceSearchStrings(element, newName)
    }

    private fun delegateProcessor(element: PsiElement): RenamePsiElementProcessor? {
        return allForElement(element).firstOrNull { it !== this }
    }

    private fun fixtureNameForRename(element: PsiElement): String? {
        return when (element) {
            is PyFunction -> PytestFixtureUtil.getFixtureName(element)
            is PyNamedParameter -> element.name
            is PyStringLiteralExpression -> element.stringValue.takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    private fun isFixtureParameter(parameter: PyNamedParameter): Boolean {
        val paramName = parameter.name ?: return false
        if (paramName == "self" || paramName == "cls") return false
        val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return false
        return PytestNaming.isTestFunction(function) || PytestFixtureUtil.isFixtureFunction(function)
    }

    private fun isUsefixturesArgument(literal: PyStringLiteralExpression): Boolean {
        val argumentList = literal.parent as? PyArgumentList ?: return false
        if (argumentList.parent !is com.jetbrains.python.psi.PyCallExpression) return false
        val decorator = PsiTreeUtil.getParentOfType(literal, PyDecorator::class.java) ?: return false
        val qName = decorator.qualifiedName?.toString() ?: return false
        return qName == "pytest.mark.usefixtures" ||
                qName == "_pytest.mark.usefixtures" ||
                qName == "mark.usefixtures" ||
                qName.endsWith(".mark.usefixtures")
    }

    private fun collectRelatedFixtures(
        element: PsiElement,
        fixtureName: String,
        context: TypeEvalContext
    ): List<PyFunction> {
        val related = LinkedHashSet<PyFunction>()
        when (element) {
            is PyFunction -> {
                val parents = PytestFixtureResolver.findParentFixtures(element, fixtureName, context)
                    .map { it.fixtureFunction }
                related.addAll(parents)
                val bases = parents.ifEmpty { listOf(element) }
                for (base in bases) {
                    PytestFixtureResolver.findOverridingFixtures(base, fixtureName)
                        .mapTo(related) { it.fixtureFunction }
                }
                related.add(element)
            }

            else -> {
                val chain = PytestFixtureResolver.findFixtureChain(element, fixtureName, context)
                    .map { it.fixtureFunction }
                related.addAll(chain)
                for (base in chain) {
                    PytestFixtureResolver.findOverridingFixtures(base, fixtureName)
                        .mapTo(related) { it.fixtureFunction }
                }
            }
        }
        return related.toList()
    }

    private fun collectFixtureParameters(
        project: com.intellij.openapi.project.Project,
        fixtureName: String,
        relatedFixtures: List<PyFunction>
    ): List<PyNamedParameter> {
        val relatedSet = relatedFixtures.toSet()
        val parameters = LinkedHashSet<PyNamedParameter>()
        val scope = GlobalSearchScope.projectScope(project)
        val searchHelper = PsiSearchHelper.getInstance(project)
        searchHelper.processElementsWithWord(
            { element, _ ->
                val parameter = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java, false)
                    ?: return@processElementsWithWord true
                if (parameter.name != fixtureName) return@processElementsWithWord true
                if (!isFixtureParameter(parameter)) return@processElementsWithWord true
                val context = TypeEvalContext.codeAnalysis(project, parameter.containingFile)
                val chain = PytestFixtureResolver.findFixtureChain(parameter, fixtureName, context)
                if (chain.any { relatedSet.contains(it.fixtureFunction) }) {
                    parameters.add(parameter)
                }
                true
            },
            scope,
            fixtureName,
            UsageSearchContext.IN_CODE,
            true
        )
        return parameters.toList()
    }

    private data class RelatedRenameTargets(
        val fixtures: List<PyFunction>,
        val parameters: List<PyNamedParameter>
    )

    private fun isFixtureDecoratorReference(reference: PsiReference): Boolean {
        val refElement = reference.element
        val decorator = PsiTreeUtil.getParentOfType(refElement, PyDecorator::class.java, false) ?: return false
        if (!PytestFixtureUtil.isFixtureDecorator(decorator)) return false
        val callee = decorator.callee as? PyExpression ?: return false
        return callee.textRange.contains(refElement.textRange)
    }
}
