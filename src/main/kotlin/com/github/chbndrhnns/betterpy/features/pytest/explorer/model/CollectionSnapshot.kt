package com.github.chbndrhnns.betterpy.features.pytest.explorer.model

data class CollectionSnapshot(
    val timestamp: Long,
    val tests: List<CollectedTest>,
    val fixtures: List<CollectedFixture>,
    val errors: List<String>,
) {
    companion object {
        fun empty(reason: String? = null) = CollectionSnapshot(
            timestamp = System.currentTimeMillis(),
            tests = emptyList(),
            fixtures = emptyList(),
            errors = listOfNotNull(reason),
        )
    }
}
