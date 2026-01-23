package com.github.chbndrhnns.betterpy.core.pytest

object PytestNodeIdUtil {

    fun normalizePytestNodeIdParts(rawParts: List<String>): List<String> {
        if (rawParts.isEmpty()) return emptyList()
        if (rawParts.size == 1) return rawParts

        val normalized = ArrayList<String>(rawParts.size + 2)
        normalized.add(rawParts.first())

        for (i in 1 until rawParts.size) {
            normalized.addAll(splitContainerSegment(rawParts[i]))
        }

        return normalized
    }

    /**
     * Pytest node ids are canonically separated by `::`.
     *
     * When `pytest-sugar` is active, the console may render the post-file container chain as a single
     * dot-separated segment (e.g. `TestGetAll.test_returns_all`), while still keeping `::` between the
     * file part and the container part.
     */
    fun splitContainerSegment(rawSegment: String): List<String> {
        val segmentWithoutParams = stripParametrization(rawSegment)
        return segmentWithoutParams
            .split('.')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun stripParametrization(name: String): String {
        val bracketIndex = name.indexOf('[')
        if (bracketIndex > 0 && name.endsWith("]")) {
            return name.substring(0, bracketIndex)
        }
        return name
    }

    fun normalizeParameterFormat(name: String): String {
        // Match pattern: name(param) where param doesn't contain nested parentheses
        // Convert to: name[param]
        val parenStart = name.lastIndexOf('(')
        val parenEnd = name.lastIndexOf(')')

        if (parenStart > 0 && parenEnd == name.length - 1 && parenEnd > parenStart) {
            val baseName = name.substring(0, parenStart)
            val param = name.substring(parenStart + 1, parenEnd)
            return "$baseName[$param]"
        }
        return name
    }

    fun appendMetainfoSuffix(baseKey: String, metainfo: String?): String {
        if (metainfo.isNullOrEmpty()) return baseKey

        val bracketStart = metainfo.indexOf('[')
        if (bracketStart == -1) return baseKey

        return baseKey + metainfo.substring(bracketStart)
    }
}
