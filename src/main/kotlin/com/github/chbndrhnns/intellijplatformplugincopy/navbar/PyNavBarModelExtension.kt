package com.github.chbndrhnns.intellijplatformplugincopy.navbar

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.util.Processor
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
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
        return getPresentableText(item, true)
    }

    override fun getPresentableText(item: Any?, forCurrentFile: Boolean): String? {
        // Check if the feature is enabled in plugin settings
        if (!PluginSettingsState.instance().state.enablePythonNavigationBar) {
            return null
        }

        val element = item as? PsiElement ?: return null
        val name = when (element) {
            is PyFunction -> element.name
            is PyClass -> element.name
            is PyTargetExpression -> element.name
            else -> null
        } ?: return null

        if (name.isDunder() || element.isOverload()) {
            return null
        }

        return when (element) {
            is PyFunction -> if (element.isAsync) "async $name()" else "$name()"
            else -> name
        }
    }

    override fun processChildren(`object`: Any, rootElement: Any?, processor: Processor<Any>): Boolean {
        val psiElement = `object` as? PsiElement ?: return true

        // Check if the feature is enabled in plugin settings
        if (!PluginSettingsState.instance().state.enablePythonNavigationBar) {
            return true
        }

        val children = when (psiElement) {
            is PyFile -> {
                val classes = psiElement.topLevelClasses.toList()
                val functions = psiElement.topLevelFunctions.toList()
                val variables = psiElement.topLevelAttributes.toList()

                sortByVisibility(classes, functions, variables)
            }

            is PyClass -> {
                val classes = psiElement.nestedClasses.toList()
                val functions = psiElement.methods.toList()
                val variables = psiElement.classAttributes.toList()

                sortByVisibility(classes, functions, variables)
            }

            else -> emptyList()
        }

        for (child in children) {
            if (shouldInclude(child)) {
                if (!processor.process(child)) return false
            }
        }

        return true
    }

    private fun sortByVisibility(
        classes: List<PsiElement>,
        functions: List<PsiElement>,
        variables: List<PsiElement>
    ): List<PsiElement> {
        val publicClasses = classes.filter { !it.isPrivate() }
        val privateClasses = classes.filter { it.isPrivate() }
        val publicFunctions = functions.filter { !it.isPrivate() }
        val privateFunctions = functions.filter { it.isPrivate() }
        val publicFields = variables.filter { !it.isPrivate() }
        val privateFields = variables.filter { it.isPrivate() }

        return publicClasses + privateClasses + publicFunctions + privateFunctions + publicFields + privateFields
    }

    private fun PsiElement.isPrivate(): Boolean {
        val name = when (this) {
            is PyFunction -> this.name
            is PyClass -> this.name
            is PyTargetExpression -> this.name
            else -> null
        }
        return name?.startsWith("_") == true && !name.isDunder()
    }

    private fun shouldInclude(element: PsiElement): Boolean {
        val name = when (element) {
            is PyFunction -> element.name
            is PyClass -> element.name
            is PyTargetExpression -> element.name
            else -> null
        } ?: return false

        return !name.isDunder() && !element.isOverload()
    }

    override fun adjustElement(psiElement: PsiElement): PsiElement {
        // Only adjust elements that belong to Python
        if (psiElement.language != language && psiElement !is PyFile) {
            return psiElement
        }

        var current = psiElement
        while (current !is PyFile && !current.isPythonMember()) {
            val parent = current.parent
            // Stop if we reach a non-Python element or null
            if (parent == null || (parent.language != language && parent !is PyFile)) {
                return current
            }
            current = parent
        }
        return current
    }

    override fun acceptParentFromModel(psiElement: PsiElement?): Boolean {
        return true
    }

    private fun PsiElement.isPythonMember(): Boolean =
        this is PyFunction || this is PyClass || this is PyTargetExpression

    private fun PsiElement.isOverload(): Boolean =
        (this as? PyFunction)?.decoratorList?.findDecorator("overload") != null

    private fun String.isDunder(): Boolean =
        startsWith("__") && endsWith("__")
}
