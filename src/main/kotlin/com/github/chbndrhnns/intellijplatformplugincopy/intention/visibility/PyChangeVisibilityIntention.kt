package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

class PyChangeVisibilityIntention : PyToggleVisibilityIntention() {
    @Volatile
    private var myText: String = "Change visibility"

    override fun getText(): String = myText
    override fun getFamilyName(): String = "Change visibility"

    override fun isAvailableForName(name: String): Boolean {
        return if (name.startsWith("_")) {
            myText = "Change visibility: make public"
            true
        } else {
            myText = "Change visibility: make private"
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
