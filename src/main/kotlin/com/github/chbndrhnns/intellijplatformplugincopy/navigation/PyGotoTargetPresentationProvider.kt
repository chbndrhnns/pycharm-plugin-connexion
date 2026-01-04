package com.github.chbndrhnns.intellijplatformplugincopy.navigation

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.navigation.GotoTargetPresentationProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

class PyGotoTargetPresentationProvider : GotoTargetPresentationProvider {
    override fun getTargetPresentation(element: PsiElement, differentNames: Boolean): TargetPresentation? {
        if (!PluginSettingsState.instance().state.enablePyGotoTargetPresentation) return null
        if (element !is PyFunction) return null
        val cls = element.containingClass
        val name = element.name ?: PyNames.UNNAMED_ELEMENT

        // Logic: if all results are named 'foo',
        // we make the searchable text 'ClassName.foo'
        val text = if (!differentNames && cls != null) "${cls.name}.$name" else name

        return TargetPresentation.builder(text)
            .containerText(cls?.name)
            .locationText(QualifiedNameFinder.findShortestImportableName(element, element.containingFile.virtualFile))
            .icon(element.getIcon(0))
            .presentation()
    }
}
