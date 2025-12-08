package com.github.chbndrhnns.intellijplatformplugincopy.testing

import com.github.chbndrhnns.intellijplatformplugincopy.searcheverywhere.PytestIdentifierResolver
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import java.util.regex.Pattern

class PytestConsoleFilter(private val project: Project) : Filter {
    companion object {
        private val PATTERN = Pattern.compile("^\\s*(.+?\\.py)::(\\S+)")
    }

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val matcher = PATTERN.matcher(line)
        if (matcher.find()) {
            val filePath = matcher.group(1)
            val nodeId = matcher.group(2)

            val startOffset = entireLength - line.length + matcher.start()
            val endOffset = entireLength - line.length + matcher.end()

            return Filter.Result(startOffset, endOffset, PytestNodeHyperlinkInfo(project, filePath, nodeId))
        }
        return null
    }

    private class PytestNodeHyperlinkInfo(
        private val project: Project,
        private val filePath: String,
        private val nodeId: String
    ) : HyperlinkInfo {

        override fun navigate(project: Project) {
            val resolver = PytestIdentifierResolver(project)
            val fullPattern = "$filePath::$nodeId"
            val element = resolver.resolve(fullPattern)
            (element as? Navigatable)?.navigate(true)
        }
    }
}
