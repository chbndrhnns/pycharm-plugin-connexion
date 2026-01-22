package com.github.chbndrhnns.intellijplatformplugincopy.features.searcheverywhere

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.Processor
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import javax.swing.ListCellRenderer

class PytestIdentifierContributor(private val myProject: Project) : WeightedSearchEverywhereContributor<PsiElement> {

    override fun getSearchProviderId(): String {
        return "PytestIdentifierContributor"
    }

    override fun getGroupName(): String {
        return "Pytest"
    }

    override fun getSortWeight(): Int {
        return 500
    }

    override fun showInFindResults(): Boolean {
        return true
    }

    override fun isShownInSeparateTab(): Boolean {
        // This will create a separate tab
        return false
    }

    override fun fetchWeightedElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in FoundItemDescriptor<PsiElement>>
    ) {
        if (!PluginSettingsState.instance().state.enablePytestIdentifierSearchEverywhereContributor) {
            return
        }

        ApplicationManager.getApplication().runReadAction {
            if (pattern.contains("::")) {
                val resolver = PytestIdentifierResolver(myProject)
                val elements = resolver.resolveAll(pattern)
                for (element in elements) {
                    consumer.process(FoundItemDescriptor(element, 1000))
                }
            }
        }
    }

    override fun processSelectedItem(selected: PsiElement, modifiers: Int, searchText: String): Boolean {
        (selected as? NavigationItem)?.navigate(true)
        return true
    }

    override fun getElementsRenderer(): ListCellRenderer<in PsiElement> {
        return object : PsiElementListCellRenderer<PsiElement>() {
            override fun getElementText(element: PsiElement): String {
                return when (element) {
                    is PyFunction -> element.name ?: ""
                    is PyClass -> element.name ?: ""
                    else -> element.toString()
                }
            }

            override fun getContainerText(element: PsiElement, name: String?): String? {
                return element.containingFile?.name
            }
        }
    }

    override fun getDataForItem(element: PsiElement, dataId: String): Any? {
        return null
    }

    class Factory : SearchEverywhereContributorFactory<PsiElement> {
        override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<PsiElement> {
            val project = requireNotNull(initEvent.project) { "Project is required for PytestIdentifierContributor" }
            return PytestIdentifierContributor(project)
        }
    }
}
