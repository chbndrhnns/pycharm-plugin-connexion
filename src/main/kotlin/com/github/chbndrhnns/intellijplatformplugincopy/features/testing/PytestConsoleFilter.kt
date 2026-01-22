package com.github.chbndrhnns.intellijplatformplugincopy.features.testing

import com.github.chbndrhnns.intellijplatformplugincopy.features.searcheverywhere.PytestIdentifierResolver
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pom.Navigatable
import java.util.regex.Pattern

class PytestConsoleFilter(private val project: Project) : Filter {
    companion object {
        private val PATTERN = Pattern.compile("^\\s*(?:(?:FAILED|ERROR)\\s+)?([^\\s]+\\.py)(?:::| - )")
    }

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val matcher = PATTERN.matcher(line)
        if (matcher.find()) {
            val filePath = matcher.group(1)
            val separator = matcher.group(0).substring(matcher.start(1) + filePath.length - matcher.start())

            if (separator.contains("::")) {
                val nodeIdStartInLine = matcher.end()
                val nodeIdEndInLine = findNodeIdEnd(line, nodeIdStartInLine)
                if (nodeIdEndInLine <= nodeIdStartInLine) return null

                val nodeId = line.substring(nodeIdStartInLine, nodeIdEndInLine)

                val startOffset = entireLength - line.length + matcher.start(1)
                val endOffset = entireLength - line.length + nodeIdEndInLine

                return Filter.Result(startOffset, endOffset, PytestNodeHyperlinkInfo(project, filePath, nodeId))
            } else if (separator.contains(" - ")) {
                val startOffset = entireLength - line.length + matcher.start(1)
                val endOffset = entireLength - line.length + matcher.start(1) + filePath.length

                return Filter.Result(startOffset, endOffset, PytestFileHyperlinkInfo(project, filePath))
            }
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

    private class PytestFileHyperlinkInfo(
        private val project: Project,
        private val filePath: String
    ) : HyperlinkInfo {

        override fun navigate(project: Project) {
            val resolver = PytestIdentifierResolver(project)
            val element = runWithModalProgressBlocking(project, "") {
                readAction {
                    resolver.resolve(filePath)
                }
            }
            (element as? Navigatable)?.navigate(true)
        }
    }

    private class PytestNodeHyperlinkInfo(
        private val project: Project,
        private val filePath: String,
        private val nodeId: String
    ) : HyperlinkInfo {

        override fun navigate(project: Project) {
            val resolver = PytestIdentifierResolver(project)
            val fullPattern = "$filePath::$nodeId"
            val element = runWithModalProgressBlocking(project, "") {
                readAction {
                    resolver.resolve(fullPattern)
                }
            }
            (element as? Navigatable)?.navigate(true)
        }
    }
}
