package fixtures

import com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.shared.PopupHost
import com.intellij.openapi.editor.Editor

class FakePopupHost : PopupHost {
    var lastTitle: String? = null
    var lastLabels: List<String> = emptyList()
    var greyedOutIndices: Set<Int> = emptySet()
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
        greyedOutIndices = emptySet()
        if (items.isNotEmpty()) {
            val idx = selectedIndex.coerceIn(0, items.size - 1)
            onChosen(items[idx])
        }
    }

    override fun <T> showChooserWithGreying(
        editor: Editor,
        title: String,
        items: List<T>,
        render: (T) -> String,
        isGreyedOut: (T) -> Boolean,
        onChosen: (T) -> Unit
    ) {
        lastTitle = title
        lastLabels = items.map {
            val baseText = render(it)
            if (isGreyedOut(it)) "$baseText (already exported)" else baseText
        }
        greyedOutIndices = items.indices.filter { isGreyedOut(items[it]) }.toSet()
        if (items.isNotEmpty()) {
            val idx = selectedIndex.coerceIn(0, items.size - 1)
            onChosen(items[idx])
        }
    }
}