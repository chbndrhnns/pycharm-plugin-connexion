package fixtures

import com.github.chbndrhnns.intellijplatformplugincopy.intention.populate.PopulateArgumentsIntentionHooks

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
