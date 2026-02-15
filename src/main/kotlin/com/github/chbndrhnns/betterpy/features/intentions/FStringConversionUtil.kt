package com.github.chbndrhnns.betterpy.features.intentions

import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * Shared utilities for f-string detection and conversion, used by both
 * [ConvertToFStringIntention] and
 * [com.github.chbndrhnns.betterpy.features.inspections.PyMissingFStringPrefixInspection].
 */
object FStringConversionUtil {

    private val PREFIX_REGEX = Regex("^([rRuUbBfFtT]*)(\"\"\"|'''|\"|')")

    /**
     * Parses the string prefix (e.g. "r", "b", "f", "") from the raw text of a string literal.
     * Returns null if the text doesn't look like a string literal.
     */
    fun parsePrefix(text: String): String? {
        val match = PREFIX_REGEX.find(text) ?: return null
        return match.groupValues[1]
    }

    /**
     * Returns true if the string already has an f-prefix or is a byte/unicode/t-string
     * (i.e. should NOT be flagged or converted).
     */
    fun shouldSkip(prefix: String): Boolean {
        return prefix.contains('f', ignoreCase = true)
                || prefix.contains('b', ignoreCase = true)
                || prefix.contains('u', ignoreCase = true)
                || prefix.contains('t', ignoreCase = true)
    }

    /**
     * Returns true if [value] (the decoded string content, without quotes/prefix)
     * contains at least one `{identifier...}` placeholder that looks like an f-string interpolation.
     */
    fun containsFStringPlaceholder(value: String): Boolean {
        var idx = 0
        while (idx < value.length) {
            val ch = value[idx]
            if (ch == '{') {
                if (idx + 1 < value.length && value[idx + 1] == '{') {
                    idx += 2
                    continue
                }
                val end = value.indexOf('}', idx + 1)
                if (end == -1) return false
                val content = value.substring(idx + 1, end).trim()
                val name = content.takeWhile { it.isLetterOrDigit() || it == '_' }
                if (isValidIdentifier(name) && content.startsWith(name)) {
                    return true
                }
                idx = end + 1
                continue
            }
            if (ch == '}' && idx + 1 < value.length && value[idx + 1] == '}') {
                idx += 2
                continue
            }
            idx++
        }
        return false
    }

    /**
     * Replaces [literal] with an f-string version (prepends "f" to the raw text).
     */
    fun convertToFString(literal: PyStringLiteralExpression) {
        val project = literal.project
        val updatedText = "f${literal.text}"
        val generator = PyElementGenerator.getInstance(project)
        val replacement = generator.createExpressionFromText(
            LanguageLevel.forElement(literal),
            updatedText
        )
        literal.replace(replacement)
    }

    private fun isValidIdentifier(name: String): Boolean {
        if (name.isEmpty()) return false
        val first = name[0]
        if (!(first == '_' || first.isLetter())) return false
        return name.all { it == '_' || it.isLetterOrDigit() }
    }
}
