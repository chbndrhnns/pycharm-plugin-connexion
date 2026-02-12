package com.github.chbndrhnns.betterpy.features.pytest.outcome

import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.intellij.openapi.diagnostic.Logger

/**
 * Parses pytest stacktraces to extract the line number where a test failed.
 *
 * Pytest outputs stacktraces in the format:
 * ```
 * test_file.py:4: AssertionError
 * ```
 *
 * This parser extracts the line number (4 in the example above).
 */
object PytestStacktraceParser {
    private val LOG = Logger.getInstance(PytestStacktraceParser::class.java)

    /**
     * Extracts the failed line number from a pytest stacktrace.
     *
     * @param stacktrace The full stacktrace from SMTestProxy.stacktrace
     * @param locationUrl The test location URL (e.g., "python<path>://test_file.test_func")
     * @return The line number where the test failed, or -1 if it cannot be determined
     */
    fun parseFailedLine(stacktrace: String?, locationUrl: String?): Int {
        LOG.debug("PytestStacktraceParser.parseFailedLine: parsing stacktrace for locationUrl='$locationUrl'")

        if (stacktrace.isNullOrBlank()) {
            LOG.debug("PytestStacktraceParser.parseFailedLine: stacktrace is null or blank, returning -1")
            return -1
        }

        if (locationUrl == null) {
            LOG.debug("PytestStacktraceParser.parseFailedLine: locationUrl is null, returning -1")
            return -1
        }

        // Extract filename from locationUrl
        val fileName = extractFileNameFromLocationUrl(locationUrl)
        if (fileName == null) {
            LOG.debug("PytestStacktraceParser.parseFailedLine: could not extract filename from locationUrl='$locationUrl', returning -1")
            return -1
        }

        LOG.debug("PytestStacktraceParser.parseFailedLine: extracted filename='$fileName' from locationUrl")

        // Try method 1: Parse from traceback marker (>) - more precise, looks for call site
        val lineFromMarker = parseLineNumberFromMarker(stacktrace, fileName)
        if (lineFromMarker != -1) {
            LOG.debug("PytestStacktraceParser.parseFailedLine: found line number $lineFromMarker using marker parsing")
            return lineFromMarker
        }

        // Try method 2: Parse "filename:lineNumber:" pattern - fallback for cases without markers
        val lineFromPattern = parseLineNumberFromPattern(stacktrace, fileName)
        if (lineFromPattern != -1) {
            LOG.debug("PytestStacktraceParser.parseFailedLine: found line number $lineFromPattern using pattern matching")
            return lineFromPattern
        }

        LOG.debug("PytestStacktraceParser.parseFailedLine: could not extract line number, returning -1")
        return -1
    }

    /**
     * Extracts the Python filename from a PyCharm location URL.
     *
     * Examples:
     * - "python<path>://test_file.test_func" -> "test_file.py"
     * - "python<path>://test_module.TestClass.test_method" -> "test_module.py"
     * - "python<path>://tests.test_.test_" -> "test_.py" (package.module.function)
     */
    private fun extractFileNameFromLocationUrl(locationUrl: String): String? {
        LOG.debug("PytestStacktraceParser.extractFileNameFromLocationUrl: extracting from '$locationUrl'")

        // Pattern: python<...>://path.to.module
        // Extract the part after ://
        val match = Regex("""python<[^>]+>://(.+)""").find(locationUrl)
        val fullPath = match?.groupValues?.get(1)

        if (fullPath == null) {
            LOG.debug("PytestStacktraceParser.extractFileNameFromLocationUrl: no match found")
            return null
        }

        LOG.debug("PytestStacktraceParser.extractFileNameFromLocationUrl: full path='$fullPath'")

        // Split by dots and find the module name
        // For "tests.test_.test_", we need "test_" (the second-to-last part before the function)
        // For "test_file.test_func", we need "test_file" (the first part)
        // For "test_module.TestClass.test_method", we need "test_module" (the first part)

        val parts = fullPath.split(".")
        LOG.debug("PytestStacktraceParser.extractFileNameFromLocationUrl: split into ${parts.size} parts: $parts")

        // Heuristic: The module name is typically the part that starts with "test_" or is before a capitalized part
        // If we have package.module.function, the module is the second-to-last part
        // If we have module.function or module.Class.method, the module is the first part

        val moduleName = parts.find { PytestNaming.isTestFunctionName(it) } ?: parts[0]

        val fileName = "$moduleName.py"
        LOG.debug("PytestStacktraceParser.extractFileNameFromLocationUrl: extracted fileName='$fileName'")
        return fileName
    }

    /**
     * Parses line number using the pattern "filename:lineNumber: ErrorType" or "filename:lineNumber: in function_name"
     * Examples:
     * - "test_sample.py:4: AssertionError"
     * - "test_.py:3: in test_"
     */
    private fun parseLineNumberFromPattern(stacktrace: String, fileName: String): Int {
        LOG.debug("PytestStacktraceParser.parseLineNumberFromPattern: searching for pattern '$fileName:(\\d+):' in stacktrace")

        // Escape special regex characters in filename
        val escapedFileName = Regex.escape(fileName)
        val regex = Regex("""$escapedFileName:(\d+):""")

        val matches = regex.findAll(stacktrace)
        val allLines = matches.map { it.groupValues[1].toIntOrNull() ?: -1 }.filter { it != -1 }.toList()

        if (allLines.isNotEmpty()) {
            LOG.debug("PytestStacktraceParser.parseLineNumberFromPattern: found ${allLines.size} line number(s): $allLines")
            // Return the last one, as it's typically the most specific error location
            // The marker method (called before this) handles finding the test call site
            val result = allLines.last()
            LOG.debug("PytestStacktraceParser.parseLineNumberFromPattern: returning last line number: $result")
            return result
        }

        LOG.debug("PytestStacktraceParser.parseLineNumberFromPattern: no line numbers found")
        return -1
    }

    /**
     * Parses line number by looking for the pytest marker (>) and finding the file:line reference near it.
     *
     * Strategy: Find the FIRST '>' marker, which represents the call site in the test.
     * Then look for the closest filename:line: reference that appears just before the marker
     * (before hitting a section separator).
     *
     * Example stacktrace:
     * ```
     * def test_():
     * >       helper()
     *
     * test_.py:5:
     * _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _
     *
     *     def helper():
     * >       1/0
     * E       ZeroDivisionError: division by zero
     *
     * test_.py:2: ZeroDivisionError
     * ```
     * In this case, we want line 5 (the reference for the first > marker showing helper() call).
     */
    private fun parseLineNumberFromMarker(stacktrace: String, fileName: String): Int {
        LOG.debug("PytestStacktraceParser.parseLineNumberFromMarker: searching for '>' marker and nearby '$fileName:(\\d+):'")

        val lines = stacktrace.lines()
        val regex = Regex("""${Regex.escape(fileName)}:(\d+):""")

        // Find the FIRST '>' marker
        var firstMarkerIndex = -1
        for (i in lines.indices) {
            if (lines[i].trimStart().startsWith(">")) {
                firstMarkerIndex = i
                LOG.debug("PytestStacktraceParser.parseLineNumberFromMarker: found first '>' marker at line $i: '${lines[i]}'")
                break
            }
        }

        if (firstMarkerIndex == -1) {
            LOG.debug("PytestStacktraceParser.parseLineNumberFromMarker: no '>' marker found")
            return -1
        }

        // Look forward from the marker (within a few lines) for filename:line: before hitting a separator
        val searchLimit = kotlin.math.min(firstMarkerIndex + 5, lines.size)
        for (j in (firstMarkerIndex + 1) until searchLimit) {
            // Stop at section separators
            if (lines[j].trim().matches(Regex("^_+$"))) {
                LOG.debug("PytestStacktraceParser.parseLineNumberFromMarker: hit section separator at line $j, stopping forward search")
                break
            }

            val match = regex.find(lines[j])
            if (match != null) {
                val lineNumber = match.groupValues[1].toIntOrNull()
                if (lineNumber != null) {
                    LOG.debug("PytestStacktraceParser.parseLineNumberFromMarker: found line number $lineNumber after first marker at line $j: '${lines[j]}'")
                    return lineNumber
                }
            }
        }

        // Fall back to looking for filename:line: after the marker anywhere (old behavior)
        for (j in (firstMarkerIndex + 1) until lines.size) {
            val match = regex.find(lines[j])
            if (match != null) {
                val lineNumber = match.groupValues[1].toIntOrNull()
                if (lineNumber != null) {
                    LOG.debug("PytestStacktraceParser.parseLineNumberFromMarker: found line number $lineNumber after marker (fallback) at line $j: '${lines[j]}'")
                    return lineNumber
                }
            }
        }

        LOG.debug("PytestStacktraceParser.parseLineNumberFromMarker: no line number found using marker method")
        return -1
    }
}
