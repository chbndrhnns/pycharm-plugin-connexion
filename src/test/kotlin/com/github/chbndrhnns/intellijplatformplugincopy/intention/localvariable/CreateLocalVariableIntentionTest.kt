package com.github.chbndrhnns.intellijplatformplugincopy.intention.localvariable

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import fixtures.TestBase
import fixtures.doIntentionTest

class CreateLocalVariableIntentionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        PluginSettingsState.instance().state.enableCreateLocalVariableIntention = true
    }

    fun testSimpleVariable() {
        myFixture.doIntentionTest(
            "test_simple.py",
            """
            def func():
                print(<caret>x)
            """,
            """
            def func():
                x = None
                print(x)
            """,
            "Create local variable"
        )
    }

    fun testInExpression() {
        myFixture.doIntentionTest(
            "test_expr.py",
            """
            def func():
                if <caret>y > 10:
                    pass
            """,
            """
            def func():
                y = None
                if y > 10:
                    pass
            """,
            "Create local variable"
        )
    }

    fun testNotAvailableForResolved() {
        myFixture.configureByText(
            "test_resolved.py", """
            def func():
                x = 10
                print(<caret>x)
        """.trimIndent()
        )

        val action = myFixture.availableIntentions.find { it.text == "Create local variable" }
        assertNull(action)
    }

    fun testNotAvailableWhenDisabled() {
        PluginSettingsState.instance().state.enableCreateLocalVariableIntention = false
        try {
            myFixture.configureByText(
                "test_disabled.py", """
                def func():
                    print(<caret>z)
            """.trimIndent()
            )

            val action = myFixture.availableIntentions.find { it.text == "Create local variable" }
            assertNull(action)
        } finally {
            PluginSettingsState.instance().state.enableCreateLocalVariableIntention = true
        }
    }
}
