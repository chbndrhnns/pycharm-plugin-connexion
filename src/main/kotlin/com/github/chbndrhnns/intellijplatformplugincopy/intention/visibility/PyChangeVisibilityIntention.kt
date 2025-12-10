package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.JbPopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.PopupHost
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

object ChangeVisibilityIntentionHooks {
    var popupHost: PopupHost? = null
}

enum class VisibilityOption(val label: String) {
    PUBLIC("Public"),
    PRIVATE("Private")
}

class PyChangeVisibilityIntention : PyToggleVisibilityIntention() {

    override fun getText(): String = "Change visibility"

    override fun getFamilyName(): String = "Change visibility"

    override fun isAvailableForName(name: String): Boolean {
        return true
    }

    override fun calcNewName(name: String): String? {
        return null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val symbol = findTargetSymbol(editor, file) ?: return

        val popupHost = ChangeVisibilityIntentionHooks.popupHost ?: JbPopupHost()

        popupHost.showChooser(
            editor = editor,
            title = "Change visibility",
            items = VisibilityOption.values().toList(),
            render = { it.label },
            onChosen = { option ->
                val currentName = symbol.name
                if (currentName != null) {
                    val newName = when (option) {
                        VisibilityOption.PUBLIC -> currentName.trimStart('_')
                        VisibilityOption.PRIVATE -> if (currentName.startsWith("_")) currentName else "_" + currentName
                    }
                    if (newName != currentName && newName.isNotEmpty()) {
                        performRename(project, symbol, newName)
                    }
                }
            }
        )
    }
}
