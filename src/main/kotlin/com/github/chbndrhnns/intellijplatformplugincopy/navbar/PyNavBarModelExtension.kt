package com.github.chbndrhnns.intellijplatformplugincopy.navbar

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression

/**
 * Navigation bar extension for Python that shows file members (classes, functions, methods)
 * in the navigation bar when "Show Members in Navigation Bar" is enabled.
 */
class PyNavBarModelExtension : StructureAwareNavBarModelExtension() {

    override val language: Language
        get() = PythonLanguage.getInstance()

    override fun getPresentableText(item: Any?): String? {
        // Check if the feature is enabled in plugin settings
        if (!PluginSettingsState.instance().state.enablePythonNavigationBar) {
            return null
        }
        
        return when (item) {
            is PyFunction -> {
                val name = item.name ?: return "function"
                if (item.isAsync) "async $name()" else "$name()"
            }

            is PyClass -> item.name ?: "class"
            is PyTargetExpression -> item.name
            else -> null
        }
    }

    override fun adjustElement(psiElement: PsiElement): PsiElement {
        return psiElement
    }

    override fun acceptParentFromModel(psiElement: PsiElement?): Boolean {
        return true
    }
}
