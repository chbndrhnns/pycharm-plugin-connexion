package com.github.chbndrhnns.betterpy.features.pytest.explorer.collection

data class CollectionResult(
    val items: List<RawCollectedItem>,
    val errors: List<String>,
) {
    companion object {
        fun empty(reason: String) = CollectionResult(emptyList(), listOf(reason))
    }
}

data class ModuleCollectionResult(
    val items: List<RawCollectedItem>,
    val errors: List<String>,
)

data class RawCollectedItem(
    val nodeId: String,
    val type: ItemType,
)

enum class ItemType { MODULE, CLASS, FUNCTION, FIXTURE }
