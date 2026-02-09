package com.github.chbndrhnns.betterpy.features.testing

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import java.util.regex.Pattern

class PythonClassConsoleFilter(private val project: Project) : Filter {
    companion object {
        // Matches <class 'dotted.path.to.Class'> or <class "dotted.path.to.Class">
        private val PATTERN = Pattern.compile("<class ['\"]([\\w.]+)['\"]>")
        private val CLASS_HIGHLIGHT_ATTRIBUTES: TextAttributes by lazy {
            val base = ConsoleViewContentType.NORMAL_OUTPUT.attributes
            val scheme = EditorColorsManager.getInstance().globalScheme
            val foreground = base.foregroundColor ?: scheme.defaultForeground
            TextAttributes(
                foreground,
                base.backgroundColor,
                foreground,
                EffectType.BOLD_DOTTED_LINE,
                base.fontType
            )
        }

        internal fun resolveClass(project: Project, qName: String): PsiElement? {
            val scope = GlobalSearchScope.allScope(project)

            for (candidateName in buildCandidateNames(project, qName)) {
                // Let's split qName into module and class short name.
                val lastDot = candidateName.lastIndexOf('.')
                val shortName = if (lastDot > 0) candidateName.substring(lastDot + 1) else candidateName
                val moduleName = if (lastDot > 0) candidateName.substring(0, lastDot) else ""
                val candidates = PyClassNameIndex.find(shortName, project, scope)

                candidates.firstOrNull { it.qualifiedName == candidateName }?.let { return it }
                if (moduleName.isNotEmpty()) {
                    candidates.firstOrNull {
                        val fileQName = it.containingFile?.let { file ->
                            QualifiedNameFinder.findCanonicalImportPath(file, null)?.toString()
                        }
                        fileQName == moduleName
                    }?.let { return it }
                }
                candidates.firstOrNull { it.name == shortName }?.let { return it }
            }
            return null
        }

        private fun buildCandidateNames(project: Project, qName: String): List<String> {
            val candidates = LinkedHashSet<String>()
            candidates.add(qName)

            val fileIndex = ProjectFileIndex.getInstance(project)
            val rootManager = ProjectRootManager.getInstance(project)
            rootManager.contentSourceRoots.forEach { sourceRoot ->
                val contentRoot = fileIndex.getContentRootForFile(sourceRoot) ?: return@forEach
                val prefixPath = VfsUtilCore.getRelativePath(sourceRoot, contentRoot, '.')
                if (prefixPath.isNullOrEmpty()) return@forEach
                val prefixWithDot = "$prefixPath."
                if (qName.startsWith(prefixWithDot)) {
                    candidates.add(qName.removePrefix(prefixWithDot))
                }
            }

            return candidates.toList()
        }
    }

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val matcher = PATTERN.matcher(line)
        if (matcher.find()) {
            val classQName = matcher.group(1)
            val startOffset = entireLength - line.length + matcher.start()
            val endOffset = entireLength - line.length + matcher.end()

            return Filter.Result(
                startOffset,
                endOffset,
                PythonClassHyperlinkInfo(project, classQName),
                CLASS_HIGHLIGHT_ATTRIBUTES
            )
        }
        return null
    }

    private class PythonClassHyperlinkInfo(
        private val project: Project,
        private val classQName: String
    ) : HyperlinkInfo {

        override fun navigate(project: Project) {
            val element = runWithModalProgressBlocking(project, "") {
                ReadAction.nonBlocking<PsiElement?> { resolveClass(project, classQName) }
                    .inSmartMode(project)
                    .executeSynchronously()
            }
            (element as? Navigatable)?.navigate(true)
        }
    }
}
