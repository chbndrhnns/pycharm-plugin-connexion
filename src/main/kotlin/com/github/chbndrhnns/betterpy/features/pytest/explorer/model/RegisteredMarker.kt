package com.github.chbndrhnns.betterpy.features.pytest.explorer.model

data class RegisteredMarker(
    val name: String,
    val description: String,
) {
    companion object {
        private val BUILTIN_MARKERS = setOf(
            "skip", "skipif", "xfail", "parametrize", "usefixtures", "filterwarnings"
        )
    }

    val isBuiltin: Boolean
        get() = name.substringBefore("(") in BUILTIN_MARKERS

    val baseName: String
        get() = name.substringBefore("(")
}
