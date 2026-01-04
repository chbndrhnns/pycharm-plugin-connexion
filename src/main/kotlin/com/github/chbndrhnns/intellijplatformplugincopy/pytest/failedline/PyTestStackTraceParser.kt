package com.github.chbndrhnns.intellijplatformplugincopy.pytest.failedline

import com.jetbrains.python.traceBackParsers.TraceBackParser

class PyTestStackTraceParser(
    myStackTrace: String?
) {

    var failedLine = -1
        private set

    init {
        if (myStackTrace != null) {
            val lines = myStackTrace.split("\n")
            for (line in lines) {
                for (parser in TraceBackParser.PARSERS) {
                    val link = parser.findLinkInTrace(line)
                    if (link != null) {
                        // In Python, line numbers are 1-based, which matches what TestStateStorage expects
                        failedLine = link.lineNumber
                        break
                    }
                }
                if (failedLine != -1) break
            }
        }
    }
}
