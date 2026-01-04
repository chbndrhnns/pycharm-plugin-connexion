package com.github.chbndrhnns.intellijplatformplugincopy.intention.localvariable

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
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
            "BetterPy: Create local variable"
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
            "BetterPy: Create local variable"
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

        val action = myFixture.availableIntentions.find { it.text == "BetterPy: Create local variable" }
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

            val action = myFixture.availableIntentions.find { it.text == "BetterPy: Create local variable" }
            assertNull(action)
        } finally {
            PluginSettingsState.instance().state.enableCreateLocalVariableIntention = true
        }
    }

    fun testPriorityAndIcon() {
        myFixture.configureByText(
            "test_priority.py", """
            def func():
                print(<caret>z)
        """.trimIndent()
        )

        var action = myFixture.availableIntentions.find { it.text == "BetterPy: Create local variable" }
        assertNotNull(action)

        while (action is com.intellij.codeInsight.intention.IntentionActionDelegate) {
            action = (action as com.intellij.codeInsight.intention.IntentionActionDelegate).delegate
        }

        assertTrue("Action should implement PriorityAction", action is PriorityAction)
        assertEquals(PriorityAction.Priority.TOP, (action as PriorityAction).priority)

        assertTrue("Action should implement Iconable", action is com.intellij.openapi.util.Iconable)
        assertEquals(AllIcons.Actions.QuickfixBulb, (action as com.intellij.openapi.util.Iconable).getIcon(0))
    }

    fun testNotAvailableForDunderVariables() {
        myFixture.configureByText(
            "test_dunder.py", """
            if <caret>__name__ == '__main__':
                pass
        """.trimIndent()
        )

        val action = myFixture.availableIntentions.find { it.text == "BetterPy: Create local variable" }
        assertNull("Intention should not be available for dunder variables", action)
    }

    fun testStandaloneVariable() {
        myFixture.doIntentionTest(
            "test_standalone.py",
            """
            def func():
                <caret>MyClass
            """,
            """
            def func():
                MyClass = None
            """,
            "BetterPy: Create local variable"
        )
    }
}
