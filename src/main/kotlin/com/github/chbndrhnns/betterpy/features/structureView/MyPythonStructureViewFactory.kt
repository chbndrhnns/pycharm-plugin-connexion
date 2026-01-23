package com.github.chbndrhnns.betterpy.features.structureView

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class MyPythonStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        // 1. Get the original builder from the default Python implementation
        // We use LanguageStructureViewBuilder to find all implementations and pick the one that isn't ours
        val originalBuilder = LanguageStructureViewBuilder.getInstance()
            .allForLanguage(psiFile.language).asSequence()
            .filter { factory -> factory !is MyPythonStructureViewFactory }
            .map { factory -> factory.getStructureViewBuilder(psiFile) }
            .firstOrNull() ?: return null

        if (!PluginSettingsState.instance().state.enableEnhancedPythonStructureView) {
            return originalBuilder
        }

        // 2. Wrap the builder
        // We must always return a TreeBasedStructureViewBuilder for the popup to work correctly
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                val originalModel = if (originalBuilder is TreeBasedStructureViewBuilder) {
                    originalBuilder.createStructureViewModel(editor)
                } else {
                    // Fallback: create the full view to get the model
                    originalBuilder.createStructureView(null, psiFile.project).treeModel
                }
                return MyStructureViewModelWrapper(originalModel)
            }

            override fun isRootNodeShown(): Boolean {
                if (originalBuilder is TreeBasedStructureViewBuilder) {
                    return originalBuilder.isRootNodeShown
                }
                return false
            }
        }
    }
}
