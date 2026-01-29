package com.github.chbndrhnns.betterpy.features.usages

import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageFilteringRule
import com.jetbrains.python.psi.PyFile

class PyTestUsageFilteringRule(private val project: Project) : UsageFilteringRule {
    override fun isVisible(usage: Usage): Boolean {
        if (usage !is PsiElementUsage) return true
        val element = usage.element ?: return true
        val file = element.containingFile
        val virtualFile = file?.virtualFile ?: return false
        val fileIndex = ProjectFileIndex.getInstance(project)
        if (fileIndex.isInTestSourceContent(virtualFile)) return true
        val pyFile = file as? PyFile ?: return false
        return PytestNaming.isTestFile(pyFile)
    }
}
