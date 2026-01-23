package com.github.chbndrhnns.betterpy.features.testing

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import java.util.regex.Pattern

class PythonClassConsoleFilter(private val project: Project) : Filter {
    companion object {
        // Matches <class 'dotted.path.to.Class'> or <class "dotted.path.to.Class">
        private val PATTERN = Pattern.compile("<class ['\"]([\\w.]+)['\"]>")
    }

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val matcher = PATTERN.matcher(line)
        if (matcher.find()) {
            val classQName = matcher.group(1)
            val startOffset = entireLength - line.length + matcher.start()
            val endOffset = entireLength - line.length + matcher.end()

            return Filter.Result(startOffset, endOffset, PythonClassHyperlinkInfo(project, classQName))
        }
        return null
    }

    private class PythonClassHyperlinkInfo(
        private val project: Project,
        private val classQName: String
    ) : HyperlinkInfo {

        override fun navigate(project: Project) {
            val element = runWithModalProgressBlocking(project, "") {
                readAction {
                    resolveClass(project, classQName)
                }
            }
            (element as? Navigatable)?.navigate(true)
        }

        private fun resolveClass(project: Project, qName: String): PsiElement? {
            val scope = GlobalSearchScope.projectScope(project)

            // Let's split qName into module and class short name.
            val lastDot = qName.lastIndexOf('.')
            if (lastDot > 0) {
                val shortName = qName.substring(lastDot + 1)
                val candidates = PyClassNameIndex.find(shortName, project, scope)
                return candidates.firstOrNull { it.qualifiedName == qName }
            } else {
                // Top level class or just short name
                val candidates = PyClassNameIndex.find(qName, project, scope)
                return candidates.firstOrNull { it.qualifiedName == qName }
            }
        }
    }
}
