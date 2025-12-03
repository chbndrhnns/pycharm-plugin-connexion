package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

class PyMakePublicIntention : PyToggleVisibilityIntention() {
    override fun getText(): String = "Make public"

    override fun getFamilyName(): String = "Toggle visibility"

    override fun isAvailableForName(name: String): Boolean {
        // Available when starts with underscore(s) but not a dunder method (filtered by base)
        return name.startsWith("_")
    }

    override fun calcNewName(name: String): String? {
        // Remove all leading underscores: __foo -> foo, _foo -> foo
        return name.trimStart('_').takeIf { it.isNotEmpty() }
    }
}
