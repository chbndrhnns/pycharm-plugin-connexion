package com.github.chbndrhnns.intellijplatformplugincopy.testing

import com.github.chbndrhnns.intellijplatformplugincopy.searcheverywhere.PytestIdentifierResolver
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import java.util.regex.Pattern

class PytestConsoleFilter(private val project: Project) : Filter {
    companion object {
        private val PATTERN = Pattern.compile("^\\s*([^\\s]+\\.py)::")
    }

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val matcher = PATTERN.matcher(line)
        if (matcher.find()) {
            val filePath = matcher.group(1)
            val nodeIdStartInLine = matcher.end()
            val nodeIdEndInLine = findNodeIdEnd(line, nodeIdStartInLine)
            if (nodeIdEndInLine <= nodeIdStartInLine) return null

            val nodeId = line.substring(nodeIdStartInLine, nodeIdEndInLine)

            val startOffset = entireLength - line.length + matcher.start(1)
            val endOffset = entireLength - line.length + nodeIdEndInLine

            return Filter.Result(startOffset, endOffset, PytestNodeHyperlinkInfo(project, filePath, nodeId))
        }
        return null
    }

    private fun findNodeIdEnd(line: String, startIndex: Int): Int {
        var bracketDepth = 0
        var parenDepth = 0
        var inSingleQuote = false
        var inDoubleQuote = false
        var escapeNext = false

        for (i in startIndex until line.length) {
            val c = line[i]

            if (escapeNext) {
                escapeNext = false
                continue
            }

            if ((inSingleQuote || inDoubleQuote) && c == '\\') {
                escapeNext = true
                continue
            }

            if (!inDoubleQuote && c == '\'') {
                inSingleQuote = !inSingleQuote
                continue
            }
            if (!inSingleQuote && c == '"') {
                inDoubleQuote = !inDoubleQuote
                continue
            }

            if (!inSingleQuote && !inDoubleQuote) {
                when (c) {
                    '[' -> bracketDepth++
                    ']' -> if (bracketDepth > 0) bracketDepth--
                    '(' -> parenDepth++
                    ')' -> if (parenDepth > 0) parenDepth--
                }

                if (bracketDepth == 0 && parenDepth == 0 && c.isWhitespace()) {
                    return i
                }
            }
        }

        return line.length
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
