package com.github.chbndrhnns.intellijplatformplugincopy.intention.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.JBUI
import javax.swing.JList

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
        val builder = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle(title)
            .setRenderer(object : SimpleListCellRenderer<T>() {
                override fun customize(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
                    text = render(value)
                    if (isGreyedOut(value)) {
                        foreground = JBUI.CurrentTheme.Label.disabledForeground()
                    }
                }
            })
            .setNamerForFiltering { value: T -> render(value) }
            .setItemChosenCallback { chosen -> onChosen(chosen) }
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)

        val popup = builder.createPopup()
        popup.showInBestPositionFor(editor)
    }
}
