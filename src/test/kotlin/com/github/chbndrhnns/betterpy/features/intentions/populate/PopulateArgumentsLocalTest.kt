package com.github.chbndrhnns.betterpy.features.intentions.populate

import fixtures.FakePopulateOptionsPopupHost
import fixtures.TestBase

class PopulateArgumentsLocalTest : TestBase() {

    fun testPopulateWithLocalVariablesFillsRequiredAndOptionalWhenMatched() {
        val optionsHost = FakePopulateOptionsPopupHost(
            selectedOptions = PopulateOptions(PopulateMode.REQUIRED_ONLY, recursive = false, useLocalScope = true)
        )
        PopulateArgumentsIntentionHooks.optionsPopupHost = optionsHost

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
        val optionsHost = FakePopulateOptionsPopupHost(
            selectedOptions = PopulateOptions(PopulateMode.REQUIRED_ONLY, recursive = false, useLocalScope = true)
        )
        PopulateArgumentsIntentionHooks.optionsPopupHost = optionsHost

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
        val optionsHost = FakePopulateOptionsPopupHost(
            selectedOptions = PopulateOptions(PopulateMode.ALL, recursive = false, useLocalScope = false)
        )
        PopulateArgumentsIntentionHooks.optionsPopupHost = optionsHost

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
        val optionsHost = FakePopulateOptionsPopupHost(
            selectedOptions = PopulateOptions(PopulateMode.ALL, recursive = false, useLocalScope = false)
        )
        PopulateArgumentsIntentionHooks.optionsPopupHost = optionsHost

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
        PopulateArgumentsIntentionHooks.optionsPopupHost = null
        super.tearDown()
    }
}
