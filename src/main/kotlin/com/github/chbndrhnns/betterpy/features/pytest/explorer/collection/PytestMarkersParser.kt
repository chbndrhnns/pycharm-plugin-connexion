package com.github.chbndrhnns.betterpy.features.pytest.explorer.collection

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.RegisteredMarker

object PytestMarkersParser {

    private val MARKER_REGEX = Regex("""^@pytest\.mark\.([^:]+):\s*(.*)$""")

    fun parse(output: String): List<RegisteredMarker> {
        return output.lineSequence()
            .mapNotNull { line ->
                MARKER_REGEX.matchEntire(line.trim())?.let { match ->
                    RegisteredMarker(
                        name = match.groupValues[1].trim(),
                        description = match.groupValues[2].trim(),
                    )
                }
            }
            .toList()
    }
}
