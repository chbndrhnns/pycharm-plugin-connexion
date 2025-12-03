package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

class PyMakePrivateIntention : PyToggleVisibilityIntention() {
    override fun getText(): String = "Make private"

    override fun getFamilyName(): String = "Toggle visibility"

    override fun isAvailableForName(name: String): Boolean {
        // Available when not starting with underscore
        return !name.startsWith("_")
    }

    override fun calcNewName(name: String): String {
        // Add single leading underscore
        return "_${name}"
    }
}
