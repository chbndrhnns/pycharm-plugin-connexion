package com.github.chbndrhnns.betterpy.features.intentions.localvariable

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import fixtures.TestBase
import fixtures.doIntentionTest

class AssignStatementToVariableIntentionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        PluginSettingsState.instance().state.enableAssignStatementToVariableIntention = true
    }

    fun testAssignCallStatement() {
        myFixture.doIntentionTest(
            "test_assign_call.py",
            """
            def func():
                <caret>load_data()
            """,
            """
            def func():
                result = load_data()
            """,
            "BetterPy: Assign statement to variable"
        )
    }

    fun testAssignCallStatementWithExistingName() {
        myFixture.doIntentionTest(
            "test_assign_call_existing.py",
            """
            def func():
                result = 1
                <caret>load_data()
            """,
            """
            def func():
                result = 1
                result1 = load_data()
            """,
            "BetterPy: Assign statement to variable"
        )
    }

    fun testNotAvailableWhenDisabled() {
        PluginSettingsState.instance().state.enableAssignStatementToVariableIntention = false
        try {
            myFixture.configureByText(
                "test_disabled.py",
                """
                def func():
                    <caret>load_data()
                """.trimIndent()
            )

            val action = myFixture.availableIntentions.find {
                it.text == "BetterPy: Assign statement to variable"
            }
            assertNull(action)
        } finally {
            PluginSettingsState.instance().state.enableAssignStatementToVariableIntention = true
        }
    }
}
