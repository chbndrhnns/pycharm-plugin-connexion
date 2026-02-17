package com.github.chbndrhnns.betterpy.features.refactoring.move.ui

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

class PyModulePathCompletionProvider(private val project: Project) :
    TextFieldWithAutoCompletionListProvider<String>(emptyList()) {

    override fun getItems(
        prefix: String?,
        cached: Boolean,
        parameters: com.intellij.codeInsight.completion.CompletionParameters?
    ): Collection<String> {
        return collectModulePaths(project)
    }

    override fun getLookupString(item: String): String = item

    private fun collectModulePaths(project: Project): Collection<String> {
        val scope = GlobalSearchScope.projectScope(project)
        val files = FilenameIndex.getAllFilesByExt(project, "py", scope)
        val psiManager = PsiManager.getInstance(project)
        val modules = linkedSetOf<String>()
        for (file in files) {
            val psiFile = psiManager.findFile(file) as? PyFile ?: continue
            val qName = QualifiedNameFinder.findCanonicalImportPath(psiFile, null)?.toString() ?: continue
            if (qName.isNotBlank()) {
                modules.add(qName)
            }
        }
        return modules
    }
}
