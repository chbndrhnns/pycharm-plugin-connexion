package com.github.chbndrhnns.betterpy.features.intentions.populate

import com.intellij.openapi.editor.Editor

/**
 * UI abstraction for the populate arguments options popup.
 */
interface PopulateOptionsPopupHost {
    fun showOptions(
        editor: Editor,
        title: String,
        recursiveAvailable: Boolean,
        localsAvailable: Boolean,
        initial: PopulateOptions,
        previewProvider: (PopulateOptions) -> String?,
        onChosen: (PopulateOptions) -> Unit
    )
}
