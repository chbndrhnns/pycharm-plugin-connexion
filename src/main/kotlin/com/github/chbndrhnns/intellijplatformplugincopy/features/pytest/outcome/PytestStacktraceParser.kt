package com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.outcome

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

        // Try method 1: Parse "filename:lineNumber:" pattern
        val lineFromPattern = parseLineNumberFromPattern(stacktrace, fileName)
        if (lineFromPattern != -1) {
            LOG.debug("PytestStacktraceParser.parseFailedLine: found line number $lineFromPattern using pattern matching")
            return lineFromPattern
        }

        // Try method 2: Parse from traceback marker (>)
        val lineFromMarker = parseLineNumberFromMarker(stacktrace, fileName)
        if (lineFromMarker != -1) {
            LOG.debug("PytestStacktraceParser.parseFailedLine: found line number $lineFromMarker using marker parsing")
            return lineFromMarker
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

        val moduleName = parts.find { it.startsWith("test_") } ?: parts[0]

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
            // Return the last one, as it's typically the assertion line or the line where the error was raised
            val result = allLines.last()
            LOG.debug("PytestStacktraceParser.parseLineNumberFromPattern: returning last line number: $result")
            return result
        }

        LOG.debug("PytestStacktraceParser.parseLineNumberFromPattern: no line numbers found")
        return -1
    }

    /**
     * Parses line number by looking for the pytest marker (>) and finding the file:line reference after it.
     *
     * Example stacktrace:
     * ```
     *     def test_failing():
     *         expected = "hello"
     *         actual = "world"
     * >       assert expected == actual
     * E       AssertionError: assert 'hello' == 'world'
     *
     * test_sample.py:4: AssertionError
     * ```
     */
    private fun parseLineNumberFromMarker(stacktrace: String, fileName: String): Int {
        LOG.debug("PytestStacktraceParser.parseLineNumberFromMarker: searching for '>' marker followed by '$fileName:(\\d+):'")

        val lines = stacktrace.lines()
        var foundMarker = false

        for (i in lines.indices) {
            val trimmedLine = lines[i].trimStart()

            // Look for the line starting with ">"
            if (trimmedLine.startsWith(">")) {
                LOG.debug("PytestStacktraceParser.parseLineNumberFromMarker: found '>' marker at line $i: '${lines[i]}'")
                foundMarker = true
                continue
            }

            // After finding marker, look for filename:line: pattern in subsequent lines
            if (foundMarker) {
                val regex = Regex("""${Regex.escape(fileName)}:(\d+):""")
                val match = regex.find(lines[i])

                if (match != null) {
                    val lineNumber = match.groupValues[1].toIntOrNull()
                    if (lineNumber != null) {
                        LOG.debug("PytestStacktraceParser.parseLineNumberFromMarker: found line number $lineNumber at line $i: '${lines[i]}'")
                        return lineNumber
                    }
                }
            }
        }

        LOG.debug("PytestStacktraceParser.parseLineNumberFromMarker: no line number found using marker method")
        return -1
    }
}
