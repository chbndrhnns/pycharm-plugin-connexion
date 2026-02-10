package com.github.chbndrhnns.betterpy.features.pytest.explorer.model

data class CollectedFixture(
    val name: String,
    val scope: String,
    val definedIn: String,
    val functionName: String,
    val dependencies: List<String>,
    val isAutouse: Boolean = false,
)
