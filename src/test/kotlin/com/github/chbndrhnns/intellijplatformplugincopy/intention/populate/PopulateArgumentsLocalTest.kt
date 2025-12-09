package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.PopupHost
import com.intellij.openapi.editor.Editor
import fixtures.TestBase

class PopulateArgumentsLocalTest : TestBase() {

    fun testPopulateWithLocalVariables() {
        val popupHost = object : PopupHost {
            override fun <T> showChooser(
                editor: Editor,
                title: String,
                items: List<T>,
                render: (T) -> String,
                onChosen: (T) -> Unit
            ) {
                // Find the option for local variables
                val item = items.find { render(it).contains("local") }
                if (item != null) {
                    onChosen(item)
                } else {
                    fail("Option with 'local' not found in chooser. Available: ${items.map { render(it) }}")
                }
            }
        }
        PopulateArgumentsIntentionHooks.popupHost = popupHost

        myFixture.configureByText(
            "a.py",
            """
            def foo(param1: int, param2: int):
                pass

            def bar():
                param1 = 123
                foo(<caret>)
            """
        )

        myFixture.launchAction(PopulateArgumentsIntention())

        val text = myFixture.file.text
        assertTrue("Should populate param1 with local variable", text.contains("param1=param1"))
        assertTrue("Should populate param2 with default value", text.contains("param2=..."))
    }
    
    override fun tearDown() {
        PopulateArgumentsIntentionHooks.popupHost = null
        super.tearDown()
    }
}
