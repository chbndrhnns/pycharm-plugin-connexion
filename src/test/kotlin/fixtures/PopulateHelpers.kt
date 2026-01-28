package fixtures

import com.github.chbndrhnns.betterpy.features.intentions.populate.PopulateArgumentsIntentionHooks

/**
 * Sets up a [FakePopupHost] for the duration of [block].
 * Automatically tears it down afterwards.
 */
inline fun withPopulatePopupSelection(index: Int, block: (FakePopupHost) -> Unit) {
    val fake = FakePopupHost().apply { selectedIndex = index }
    PopulateArgumentsIntentionHooks.popupHost = fake
    try {
        block(fake)
    } finally {
        PopulateArgumentsIntentionHooks.popupHost = null
    }
}

/**
 * Sets up a [FakePopupHost] with a sequence of selections for multiple popups.
 */
inline fun withPopulatePopupSelections(indices: List<Int>, block: (FakePopupHost) -> Unit) {
    val fake = FakePopupHost().apply {
        selectionQueue.addAll(indices)
        selectedIndex = indices.lastOrNull() ?: 0
    }
    PopulateArgumentsIntentionHooks.popupHost = fake
    try {
        block(fake)
    } finally {
        PopulateArgumentsIntentionHooks.popupHost = null
    }
}
