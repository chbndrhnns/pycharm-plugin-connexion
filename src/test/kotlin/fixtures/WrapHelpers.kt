package fixtures

import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.WrapWithExpectedTypeIntentionHooks

/**
 * Sets up a [FakePopupHost] for the duration of [block].
 * Automatically tears it down afterwards.
 */
inline fun withWrapPopupSelection(index: Int, block: (FakePopupHost) -> Unit) {
    val fake = FakePopupHost().apply { selectedIndex = index }
    WrapWithExpectedTypeIntentionHooks.popupHost = fake
    try {
        block(fake)
    } finally {
        WrapWithExpectedTypeIntentionHooks.popupHost = null
    }
}
