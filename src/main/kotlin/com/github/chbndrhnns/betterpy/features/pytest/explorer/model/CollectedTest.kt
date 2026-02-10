package com.github.chbndrhnns.betterpy.features.pytest.explorer.model

data class CollectedTest(
    val nodeId: String,
    val modulePath: String,
    val className: String?,
    val functionName: String,
    val fixtures: List<String>,
    val parametrizeIds: List<String> = emptyList(),
)
