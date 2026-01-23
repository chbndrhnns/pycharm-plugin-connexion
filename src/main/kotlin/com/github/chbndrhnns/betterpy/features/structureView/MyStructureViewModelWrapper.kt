package com.github.chbndrhnns.betterpy.features.structureView

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.ide.structureView.FileEditorPositionListener
import com.intellij.ide.structureView.ModelListener
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.Filter
import com.intellij.ide.util.treeView.smartTree.Grouper
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.openapi.util.Disposer

class MyStructureViewModelWrapper(private val delegate: StructureViewModel) : StructureViewModel {

    override fun getFilters(): Array<Filter> {
        val originalFilters = delegate.filters
        if (!PluginSettingsState.instance().state.enableStructureViewPrivateMembersFilter) {
            return originalFilters
        }
        val newFilters = originalFilters + MyPrivateMembersFilter()
        return newFilters
    }

    override fun getRoot(): StructureViewTreeElement {
        return delegate.root
    }

    override fun getGroupers(): Array<Grouper> = delegate.groupers
    override fun getSorters(): Array<Sorter> = delegate.sorters
    override fun getCurrentEditorElement(): Any? = delegate.currentEditorElement
    override fun addEditorPositionListener(listener: FileEditorPositionListener) = delegate.addEditorPositionListener(listener)
    override fun removeEditorPositionListener(listener: FileEditorPositionListener) = delegate.removeEditorPositionListener(listener)
    override fun addModelListener(modelListener: ModelListener) = delegate.addModelListener(modelListener)
    override fun removeModelListener(modelListener: ModelListener) = delegate.removeModelListener(modelListener)
    override fun dispose() = Disposer.dispose(delegate)
    override fun shouldEnterElement(element: Any?): Boolean = delegate.shouldEnterElement(element)
}
