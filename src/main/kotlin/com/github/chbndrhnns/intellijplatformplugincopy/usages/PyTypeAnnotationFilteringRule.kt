package com.github.chbndrhnns.intellijplatformplugincopy.usages

import com.intellij.openapi.project.Project
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageFilteringRule
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTypeAnnotationFilteringRule(private val myProject: Project) : UsageFilteringRule {
    override fun getActionId(): String {
        // Must match the ID in plugin.xml
        return "Python.ShowTypeAnnotations"
    }

    override fun isVisible(usage: Usage): Boolean {
        // We want to EXCLUDE (hide) type annotations, and show everything else.

        if (usage is PsiElementUsage) {
            val element = usage.element
            if (element != null) {
                val context = TypeEvalContext.userInitiated(myProject, element.containingFile)


                // If it IS inside a type hint, hide it (return false)
                if (PyTypingTypeProvider.isInsideTypeHint(element, context)) {
                    return false
                }
            }
        }

        // Default to showing usages that are NOT type hints
        return true
    }
}