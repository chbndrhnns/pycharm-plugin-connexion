package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapWithExpectedTypeIntention
import com.intellij.openapi.editor.Editor

class FakePopupHost : WrapWithExpectedTypeIntention.PopupHost {
    var lastTitle: String? = null
    var lastLabels: List<String> = emptyList()
    var selectedIndex: Int = 0

    override fun <T> showChooser(
        editor: Editor,
        title: String,
        items: List<T>,
        render: (T) -> String,
        onChosen: (T) -> Unit
    ) {
        lastTitle = title
        lastLabels = items.map(render)
        val idx = selectedIndex.coerceIn(0, items.size - 1)
        onChosen(items[idx])
    }
}
