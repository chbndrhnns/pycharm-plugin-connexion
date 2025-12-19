package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants

class PyChangeVisibilityIntention : PyToggleVisibilityIntention() {
    @Volatile
    private var myText: String = PluginConstants.ACTION_PREFIX + "Change visibility"

    override fun getText(): String = myText
    override fun getFamilyName(): String = "Change visibility"

    override fun isAvailableForName(name: String): Boolean {
        return if (name.startsWith("_")) {
            myText = PluginConstants.ACTION_PREFIX + "Change visibility: make public"
            true
        } else {
            myText = PluginConstants.ACTION_PREFIX + "Change visibility: make private"
            true
        }
    }

    override fun calcNewName(name: String): String? {
        return if (name.startsWith("_")) {
            val newName = name.trimStart('_')
            if (newName.isNotEmpty()) newName else null
        } else {
            "_$name"
        }
    }
}
