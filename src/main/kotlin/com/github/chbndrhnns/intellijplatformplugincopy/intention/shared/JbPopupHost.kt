package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.SimpleListCellRenderer

/** Default PopupHost implementation backed by the IntelliJ platform UI. */
class JbPopupHost : PopupHost {
    override fun <T> showChooser(
        editor: Editor,
        title: String,
        items: List<T>,
        render: (T) -> String,
        onChosen: (T) -> Unit
    ) {
        val builder = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle(title)
            .setRenderer(SimpleListCellRenderer.create<T>("") { value -> render(value) })
            .setNamerForFiltering { value: T -> render(value) }
            .setItemChosenCallback { chosen -> onChosen(chosen) }
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)

        val popup = builder.createPopup()
        popup.showInBestPositionFor(editor)
    }

    override fun <T> showChooserWithGreying(
        editor: Editor,
        title: String,
        items: List<T>,
        render: (T) -> String,
        isGreyedOut: (T) -> Boolean,
        onChosen: (T) -> Unit
    ) {
        val step = object : BaseListPopupStep<T>(title, items) {
            override fun getTextFor(value: T): String {
                val baseText = render(value)
                return if (isGreyedOut(value)) "$baseText (already exported)" else baseText
            }

            override fun isSelectable(value: T): Boolean = !isGreyedOut(value)

            override fun onChosen(selectedValue: T, finalChoice: Boolean): PopupStep<*>? {
                onChosen(selectedValue)
                return FINAL_CHOICE
            }
        }

        val popup = JBPopupFactory.getInstance().createListPopup(step)
        popup.showInBestPositionFor(editor)
    }
}
