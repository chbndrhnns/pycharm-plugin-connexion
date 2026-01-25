package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext

class PytestFixtureRenameProcessor : RenamePsiElementProcessor() {
    private val log = Logger.getInstance(PytestFixtureRenameProcessor::class.java)

    override fun canProcessElement(element: PsiElement): Boolean {
        return element is PyFunction &&
                PytestFixtureFeatureToggle.isEnabled() &&
                PytestFixtureUtil.isFixtureFunction(element)
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val function = element as? PyFunction ?: return
        if (!PytestFixtureFeatureToggle.isEnabled()) return

        val fixtureName = PytestFixtureUtil.getFixtureName(function) ?: return
        if (function.name != fixtureName) {
            return
        }

        val relatedFixtures = ReadAction.compute<List<PyFunction>, RuntimeException> {
            val context = TypeEvalContext.codeAnalysis(function.project, function.containingFile)
            val related = LinkedHashSet<PyFunction>()
            val parents = PytestFixtureResolver.findParentFixtures(function, fixtureName, context)
                .map { it.fixtureFunction }
            related.addAll(parents)

            val bases = parents.ifEmpty { listOf(function) }
            for (base in bases) {
                PytestFixtureResolver.findOverridingFixtures(base, fixtureName)
                    .mapTo(related) { it.fixtureFunction }
            }
            related.remove(function)
            related.toList()
        }

        if (relatedFixtures.isEmpty()) return
        if (log.isDebugEnabled) {
            log.debug("PytestFixtureRenameProcessor: renaming ${relatedFixtures.size} related fixture(s) for '$fixtureName'")
        }

        for (fixture in relatedFixtures) {
            allRenames[fixture] = newName
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

    private fun isFixtureDecoratorReference(reference: PsiReference): Boolean {
        val refElement = reference.element
        val decorator = PsiTreeUtil.getParentOfType(refElement, PyDecorator::class.java, false) ?: return false
        if (!PytestFixtureUtil.isFixtureDecorator(decorator)) return false
        val callee = decorator.callee as? PyExpression ?: return false
        return callee.textRange.contains(refElement.textRange)
    }
}
