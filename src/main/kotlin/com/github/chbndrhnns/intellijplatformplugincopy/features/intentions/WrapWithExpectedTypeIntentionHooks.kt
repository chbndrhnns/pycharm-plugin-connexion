package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions

import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.shared.PopupHost
import org.jetbrains.annotations.TestOnly

/**
 * Test-only hooks for WrapWithExpectedTypeIntention.
 *
 * Placed outside the EP implementation class to comply with the guideline:
 * companion objects in IDE extension implementations may only contain a logger and constants.
 */
object WrapWithExpectedTypeIntentionHooks {
    /** Optional popup host injection used by tests to simulate chooser selection. */
    @TestOnly
    @JvmStatic
    var popupHost: PopupHost? = null
}
