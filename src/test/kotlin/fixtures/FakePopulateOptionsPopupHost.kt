package fixtures

import com.github.chbndrhnns.betterpy.features.intentions.populate.PopulateMode
import com.github.chbndrhnns.betterpy.features.intentions.populate.PopulateOptions
import com.github.chbndrhnns.betterpy.features.intentions.populate.PopulateOptionsPopupHost
import com.intellij.openapi.editor.Editor

class FakePopulateOptionsPopupHost(
    var selectedOptions: PopulateOptions = PopulateOptions(PopulateMode.ALL, recursive = false)
) : PopulateOptionsPopupHost {
    var lastTitle: String? = null
    var lastModeLabels: List<String> = emptyList()
    var lastRecursiveAvailable: Boolean = false
    var lastLocalsAvailable: Boolean = false
    var lastInitial: PopulateOptions? = null

    override fun showOptions(
        editor: Editor,
        title: String,
        recursiveAvailable: Boolean,
        localsAvailable: Boolean,
        initial: PopulateOptions,
        onChosen: (PopulateOptions) -> Unit
    ) {
        lastTitle = title
        lastModeLabels = listOf("All arguments", "Required arguments only")
        lastRecursiveAvailable = recursiveAvailable
        lastLocalsAvailable = localsAvailable
        lastInitial = initial
        onChosen(selectedOptions)
    }
}
