package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.lang.CodeInsightActions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.SimpleListCellRenderer
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.types.TypeEvalContext

private const val GOTO_SUPER_FIXTURE_TITLE = "Choose super fixture"

class PytestFixtureGotoSuperHandler : CodeInsightActionHandler {
    private val log = Logger.getInstance(PytestFixtureGotoSuperHandler::class.java)

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            if (log.isDebugEnabled) {
                log.debug("PytestFixtureGotoSuperHandler: feature disabled, delegating to default handler")
            }
            delegateToDefault(project, editor, file, null)
            return
        }
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)
        val function = element?.let { PsiTreeUtil.getParentOfType(it, PyFunction::class.java, false) }
        val context = TypeEvalContext.codeAnalysis(project, function?.containingFile ?: file)
        val selection = selectFixtureTarget(element, function, context)
        if (selection == null) {
            if (log.isDebugEnabled) {
                log.debug("PytestFixtureGotoSuperHandler: no fixture at caret, delegating to default handler")
            }
            delegateToDefault(project, editor, file, element)
            return
        }

        val parents = selection.links
        if (parents.isEmpty()) {
            if (log.isDebugEnabled) {
                log.debug("PytestFixtureGotoSuperHandler: no parent fixtures for '${selection.fixtureName}', delegating to default handler")
            }
            delegateToDefault(project, editor, file, element)
            return
        }
        if (log.isDebugEnabled) {
            log.debug("PytestFixtureGotoSuperHandler: showing ${parents.size} parent fixture(s) for '${selection.fixtureName}'")
        }

        val targets = LinkedHashMap<PyFunction, PopupTarget>()
        for (link in parents) {
            val function = link.fixtureFunction
            if (function !in targets) {
                targets[function] = PopupTarget(link, formatFixtureDisplayText(link))
            }
        }

        val popupTargets = targets.values.toList()
        if (popupTargets.size == 1) {
            if (log.isDebugEnabled) {
                log.debug("PytestFixtureGotoSuperHandler: navigating to single parent for '${selection.fixtureName}'")
            }
            popupTargets[0].navigate()
            return
        }

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(popupTargets)
            .setTitle(GOTO_SUPER_FIXTURE_TITLE)
            .setNamerForFiltering { value -> value.displayText }
            .setRenderer(SimpleListCellRenderer.create<PopupTarget> { label, value, _ ->
                label.text = value.displayText
            })
            .setItemChosenCallback { it.navigate() }
            .createPopup()
        popup.showInBestPositionFor(editor)
    }

    private fun selectFixtureTarget(
        element: PsiElement?,
        function: PyFunction?,
        context: TypeEvalContext
    ): FixtureTargetSelection? {
        if (function != null && PytestFixtureUtil.isFixtureFunction(function)) {
            val fixtureName = PytestFixtureUtil.getFixtureName(function) ?: return null
            val parents = PytestFixtureResolver.findParentFixtures(function, fixtureName, context)
            return FixtureTargetSelection(fixtureName, parents)
        }

        val parameter = element?.let { findParameterAtOrNear(it) }
        if (parameter != null) {
            val name = parameter.name ?: return null
            if (name == "self" || name == "cls") return null
            val containingFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java) ?: return null
            if (!isTestOrFixtureFunction(containingFunction)) return null
            val chain = PytestFixtureResolver.findFixtureChain(parameter, name, context)
            return FixtureTargetSelection(name, chain)
        }

        val reference = element?.let { PsiTreeUtil.getParentOfType(it, PyReferenceExpression::class.java, false) }
            ?: (element as? PyReferenceExpression)
        if (reference != null) {
            val name = reference.name ?: return null
            if (reference.qualifier != null || name == "self" || name == "cls") return null
            val containingFunction = PsiTreeUtil.getParentOfType(reference, PyFunction::class.java) ?: return null
            if (!isTestOrFixtureFunction(containingFunction)) return null
            val statementList = containingFunction.statementList
            if (!PsiTreeUtil.isAncestor(statementList, reference, false)) return null
            if (containingFunction.parameterList.parameters.none { it.name == name }) return null
            val chain = PytestFixtureResolver.findFixtureChain(reference, name, context)
            return FixtureTargetSelection(name, chain)
        }

        return null
    }

    private fun findParameterAtOrNear(element: PsiElement): PyNamedParameter? {
        if (element is PyNamedParameter) return element
        PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java)?.let { return it }
        val prevLeaf = PsiTreeUtil.prevLeaf(element, true)
        if (prevLeaf != null) {
            PsiTreeUtil.getParentOfType(prevLeaf, PyNamedParameter::class.java)?.let { return it }
        }
        val nextLeaf = PsiTreeUtil.nextLeaf(element, true)
        if (nextLeaf != null) {
            PsiTreeUtil.getParentOfType(nextLeaf, PyNamedParameter::class.java)?.let { return it }
        }
        return null
    }

    private fun isTestOrFixtureFunction(function: PyFunction): Boolean {
        if (PytestNaming.isTestFunction(function)) {
            return true
        }
        return PytestFixtureUtil.isFixtureFunction(function)
    }

    private fun delegateToDefault(project: Project, editor: Editor, file: PsiFile, element: PsiElement?) {
        val language = element?.let { PsiUtilCore.getLanguageAtOffset(file, it.textOffset) } ?: file.language
        val handlers = CodeInsightActions.GOTO_SUPER.allForLanguage(language)
        val fallback = handlers.firstOrNull { it.javaClass != this.javaClass }
        fallback?.invoke(project, editor, file)
    }

    internal fun buildPopupDisplayTexts(links: List<FixtureLink>): List<String> {
        return links.map { formatFixtureDisplayText(it) }
    }

    private fun formatFixtureDisplayText(link: FixtureLink): String {
        val container = fixtureContainer(link)
        return if (container != null) "${link.fixtureName} ($container)" else link.fixtureName
    }

    private fun fixtureContainer(link: FixtureLink): String? {
        val function = link.fixtureFunction
        val fileModule = moduleName(function.containingFile)

        val classFqn = function.containingClass?.let { cls ->
            val classChain = buildClassChain(cls)
            fileModule?.let { "$it.$classChain" } ?: classChain
        }

        return classFqn ?: fileModule
    }

    private fun buildClassChain(cls: PyClass): String {
        val classNames = mutableListOf<String>()
        var current: PyClass? = cls
        while (current != null) {
            classNames.add(current.name ?: PyNames.UNNAMED_ELEMENT)
            current = PsiTreeUtil.getParentOfType(current, PyClass::class.java, true)
        }
        return classNames.asReversed().joinToString(".")
    }

    private fun moduleName(file: PsiFile?): String? {
        file ?: return null
        return QualifiedNameFinder.findCanonicalImportPath(file, null)?.toString()
            ?: file.name.removeSuffix(".py")
    }

    private data class PopupTarget(
        val link: FixtureLink,
        val displayText: String
    ) {
        fun navigate() {
            (link.fixtureFunction as? Navigatable)?.navigate(true)
        }
    }

    private data class FixtureTargetSelection(
        val fixtureName: String,
        val links: List<FixtureLink>
    )
}
