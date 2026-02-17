package com.github.chbndrhnns.betterpy.features.pytest.explorer.model

data class CollectedTest(
    val nodeId: String,
    val modulePath: String,
    val className: String?,
    val functionName: String,
    val fixtures: List<String>,
    val parametrizeIds: List<String> = emptyList(),
    val markers: List<String> = emptyList(),
) {
    val isSkipped: Boolean
        get() = markers.any { it == "skip" || it == "skipif" }
}
