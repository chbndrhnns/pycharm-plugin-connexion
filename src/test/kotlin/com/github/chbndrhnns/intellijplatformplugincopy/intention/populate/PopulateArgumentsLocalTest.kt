package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PopupHost
import com.intellij.openapi.editor.Editor
import fixtures.TestBase

class PopulateArgumentsLocalTest : TestBase() {

    fun testPopulateWithLocalVariablesFillsRequiredAndOptionalWhenMatched() {
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

            override fun <T> showChooserWithGreying(
                editor: Editor,
                title: String,
                items: List<T>,
                render: (T) -> String,
                isGreyedOut: (T) -> Boolean,
                onChosen: (T) -> Unit
            ) {
                showChooser(editor, title, items, render, onChosen)
            }
        }
        PopulateArgumentsIntentionHooks.popupHost = popupHost

        myFixture.configureByText(
            "a.py",
            """
            def foo(param1: int, param2: int = 0):
                pass

            def bar():
                param1 = 123
                param2 = 456
                foo(<caret>)
            """
        )

        myFixture.launchAction(PopulateArgumentsIntention())

        val text = myFixture.file.text
        assertTrue("Should populate param1 with local variable", text.contains("param1=param1"))
        assertTrue(
            "Should populate optional/defaulted param2 when a local match exists",
            text.contains("param2=param2")
        )
        assertFalse("Locals mode must not insert ellipsis", text.contains("..."))
    }

    fun testPopulateWithLocalVariablesDoesNotFillOptionalWhenNoMatch() {
        val popupHost = object : PopupHost {
            override fun <T> showChooser(
                editor: Editor,
                title: String,
                items: List<T>,
                render: (T) -> String,
                onChosen: (T) -> Unit
            ) {
                val item = items.find { render(it).contains("local") }
                if (item != null) {
                    onChosen(item)
                } else {
                    fail("Option with 'local' not found in chooser. Available: ${items.map { render(it) }}")
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
                showChooser(editor, title, items, render, onChosen)
            }
        }
        PopulateArgumentsIntentionHooks.popupHost = popupHost

        myFixture.configureByText(
            "a.py",
            """
            def foo(param1: int, param2: int = 0):
                pass

            def bar():
                param1 = 123
                foo(<caret>)
            """
        )

        myFixture.launchAction(PopulateArgumentsIntention())

        val text = myFixture.file.text
        assertTrue("Should populate param1 with local variable", text.contains("param1=param1"))
        assertFalse(
            "Should not populate optional/defaulted param2 when no local match exists",
            text.contains("param2=")
        )
        assertFalse("Locals mode must not insert ellipsis", text.contains("..."))
    }

    fun testPopulateUsesImportAliasForClass() {
        val popupHost = object : PopupHost {
            override fun <T> showChooser(
                editor: Editor,
                title: String,
                items: List<T>,
                render: (T) -> String,
                onChosen: (T) -> Unit
            ) {
                // Find "All arguments"
                val item = items.find { render(it) == "All arguments" }
                if (item != null) {
                    onChosen(item)
                } else {
                    fail("Option 'All arguments' not found.")
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
                showChooser(editor, title, items, render, onChosen)
            }
        }
        PopulateArgumentsIntentionHooks.popupHost = popupHost

        myFixture.configureByText(
            "a.py",
            """
            from datetime import date as MyDate
            
            def foo(d: MyDate):
                pass

            def bar():
                foo(<caret>)
            """
        )

        myFixture.launchAction(PopulateArgumentsIntention())

        val text = myFixture.file.text
        assertTrue("Should use alias MyDate, but got: " + text, text.contains("d=MyDate()"))
    }

    fun testPopulateUsesModuleAlias() {
        val popupHost = object : PopupHost {
            override fun <T> showChooser(
                editor: Editor,
                title: String,
                items: List<T>,
                render: (T) -> String,
                onChosen: (T) -> Unit
            ) {
                val item = items.find { render(it) == "All arguments" }!!
                onChosen(item)
            }

            override fun <T> showChooserWithGreying(
                editor: Editor,
                title: String,
                items: List<T>,
                render: (T) -> String,
                isGreyedOut: (T) -> Boolean,
                onChosen: (T) -> Unit
            ) {
                showChooser(editor, title, items, render, onChosen)
            }
        }
        PopulateArgumentsIntentionHooks.popupHost = popupHost

        myFixture.configureByText(
            "a.py",
            """
            import datetime as dt
            
            def foo(d: dt.date):
                pass

            def bar():
                foo(<caret>)
            """
        )

        myFixture.launchAction(PopulateArgumentsIntention())

        val text = myFixture.file.text
        assertTrue("Should use module alias dt.date, got: " + text, text.contains("d=dt.date("))
    }

    override fun tearDown() {
        PopulateArgumentsIntentionHooks.popupHost = null
        super.tearDown()
    }
}
