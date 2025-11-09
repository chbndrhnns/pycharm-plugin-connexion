package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.ui

import com.intellij.openapi.editor.Editor

/** Simple UI abstraction to display a chooser and return a selection. */
interface PopupHost {
    fun <T> showChooser(
        editor: Editor,
        title: String,
        items: List<T>,
        render: (T) -> String,
        onChosen: (T) -> Unit
    )
}
