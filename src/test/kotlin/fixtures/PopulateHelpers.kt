package fixtures

import com.github.chbndrhnns.betterpy.features.intentions.populate.PopulateArgumentsIntentionHooks
import com.github.chbndrhnns.betterpy.features.intentions.populate.PopulateMode
import com.github.chbndrhnns.betterpy.features.intentions.populate.PopulateOptions

/**
 * Sets up a [FakePopupHost] for the duration of [block].
 * Automatically tears it down afterwards.
 */
inline fun withPopulatePopupSelection(index: Int, block: (FakePopulateOptionsPopupHost) -> Unit) {
    val selected = when (index) {
        0 -> PopulateOptions(PopulateMode.ALL, recursive = false, useLocalScope = false)
        1 -> PopulateOptions(PopulateMode.REQUIRED_ONLY, recursive = false, useLocalScope = false)
        2 -> PopulateOptions(PopulateMode.ALL, recursive = true, useLocalScope = false)
        3 -> PopulateOptions(PopulateMode.REQUIRED_ONLY, recursive = true, useLocalScope = false)
        4 -> PopulateOptions(PopulateMode.REQUIRED_ONLY, recursive = false, useLocalScope = true)
        else -> PopulateOptions(PopulateMode.ALL, recursive = false, useLocalScope = false)
    }
    val fake = FakePopulateOptionsPopupHost(selectedOptions = selected)
    PopulateArgumentsIntentionHooks.optionsPopupHost = fake
    try {
        block(fake)
    } finally {
        PopulateArgumentsIntentionHooks.optionsPopupHost = null
    }
}

/**
 * Sets up a [FakePopupHost] with a sequence of selections for multiple popups.
 */
inline fun withPopulatePopupSelections(
    optionsIndex: Int,
    unionIndices: List<Int>,
    block: (FakePopulateOptionsPopupHost, FakePopupHost) -> Unit
) {
    val options = when (optionsIndex) {
        0 -> PopulateOptions(PopulateMode.ALL, recursive = false, useLocalScope = false)
        1 -> PopulateOptions(PopulateMode.REQUIRED_ONLY, recursive = false, useLocalScope = false)
        2 -> PopulateOptions(PopulateMode.ALL, recursive = true, useLocalScope = false)
        3 -> PopulateOptions(PopulateMode.REQUIRED_ONLY, recursive = true, useLocalScope = false)
        4 -> PopulateOptions(PopulateMode.REQUIRED_ONLY, recursive = false, useLocalScope = true)
        else -> PopulateOptions(PopulateMode.ALL, recursive = false, useLocalScope = false)
    }
    val optionsHost = FakePopulateOptionsPopupHost(selectedOptions = options)
    val unionHost = FakePopupHost().apply {
        selectionQueue.addAll(unionIndices)
        selectedIndex = unionIndices.lastOrNull() ?: 0
    }
    PopulateArgumentsIntentionHooks.optionsPopupHost = optionsHost
    PopulateArgumentsIntentionHooks.popupHost = unionHost
    try {
        block(optionsHost, unionHost)
    } finally {
        PopulateArgumentsIntentionHooks.optionsPopupHost = null
        PopulateArgumentsIntentionHooks.popupHost = null
    }
}
