package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.TypeEvalContext

class PytestFixtureFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean {
        if (!PytestFixtureFeatureToggle.isEnabled()) return false
        val function = element as? PyFunction ?: return false
        return PytestFixtureUtil.isFixtureFunction(function)
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
        if (!PytestFixtureFeatureToggle.isEnabled()) return null
        val function = element as? PyFunction ?: return null
        val fixtureName = PytestFixtureUtil.getFixtureName(function) ?: return null
        val context = TypeEvalContext.codeAnalysis(function.project, function.containingFile)
        val related = collectRelatedFixtures(function, fixtureName, context)
        val parameters = collectFixtureParameters(function.project, fixtureName, related)
        return PytestFixtureFindUsagesHandler(function, related, parameters)
    }
}

private class PytestFixtureFindUsagesHandler(
    element: PyFunction,
    private val relatedFixtures: List<PyFunction>,
    private val relatedParameters: List<PyNamedParameter>
) : FindUsagesHandler(element) {

    override fun getSecondaryElements(): Array<PsiElement> = relatedParameters.toTypedArray()

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Boolean {
        if (!super.processElementUsages(element, processor, options)) return false
        if (element is PyFunction) {
            val usages = ReadAction.compute<List<UsageInfo>, RuntimeException> {
                relatedFixtures
                    .asSequence()
                    .filter { it != element }
                    .map { it.nameIdentifier ?: it }
                    .map { UsageInfo(it) }
                    .toList()
            }
            for (usage in usages) {
                if (!processor.process(usage)) return false
            }
        }
        return true
    }
}

private fun collectRelatedFixtures(
    element: PyFunction,
    fixtureName: String,
    context: TypeEvalContext
): List<PyFunction> {
    val related = LinkedHashSet<PyFunction>()
    val parents = PytestFixtureResolver.findParentFixtures(element, fixtureName, context)
        .map { it.fixtureFunction }
    related.addAll(parents)
    val bases = parents.ifEmpty { listOf(element) }
    for (base in bases) {
        PytestFixtureResolver.findOverridingFixtures(base, fixtureName)
            .mapTo(related) { it.fixtureFunction }
    }
    related.add(element)
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
            val elementFile = element.containingFile ?: return@processElementsWithWord true
            if (!elementFile.viewProvider.isPhysical) return@processElementsWithWord true
            val elementVFile = elementFile.virtualFile
            if (elementVFile == null || !elementVFile.isInLocalFileSystem) return@processElementsWithWord true
            val parameter = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java, false)
                ?: return@processElementsWithWord true
            if (!parameter.isValid || !parameter.isPhysical) return@processElementsWithWord true
            val parameterFile = parameter.containingFile ?: return@processElementsWithWord true
            if (!parameterFile.viewProvider.isPhysical) return@processElementsWithWord true
            val parameterVFile = parameterFile.virtualFile
            if (parameterVFile == null || !parameterVFile.isInLocalFileSystem) return@processElementsWithWord true
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

private fun isFixtureParameter(parameter: PyNamedParameter): Boolean {
    val paramName = parameter.name ?: return false
    if (paramName == "self" || paramName == "cls") return false
    val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return false
    return PytestNaming.isTestFunction(function) || PytestFixtureUtil.isFixtureFunction(function)
}
