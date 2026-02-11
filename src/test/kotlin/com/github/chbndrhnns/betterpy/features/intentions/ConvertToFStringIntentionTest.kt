package com.github.chbndrhnns.betterpy.features.intentions

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import fixtures.TestBase
import fixtures.doIntentionTest

class ConvertToFStringIntentionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        PluginSettingsState.instance().state.enableConvertToFStringIntention = true
    }

    fun testSimpleStringConversion() {
        myFixture.doIntentionTest(
            "test_simple.py",
            """
            def greet(name):
                message = "<caret>Hello {name}"
            """,
            """
            def greet(name):
                message = f"Hello {name}"
            """,
            "BetterPy: Convert to f-string"
        )
    }

    fun testMultilineStringConversion() {
        val triple = "\"\"\""
        myFixture.doIntentionTest(
            "test_multiline.py",
            """
            def build_query(table, id):
                query = ${triple}<caret>SELECT * FROM {table}
                WHERE id = {id}
                ${triple}
            """,
            """
            def build_query(table, id):
                query = f${triple}SELECT * FROM {table}
                WHERE id = {id}
                ${triple}
            """,
            "BetterPy: Convert to f-string"
        )
    }

    fun testRawStringConversion() {
        myFixture.doIntentionTest(
            "test_raw.py",
            """
            def build_path(root):
                path = r"<caret>{root}/bin"
            """,
            """
            def build_path(root):
                path = fr"{root}/bin"
            """,
            "BetterPy: Convert to f-string"
        )
    }

    fun testNotAvailableWhenAlreadyFString() {
        myFixture.configureByText(
            "test_fstring.py",
            """
            def greet(name):
                message = f"<caret>Hello {name}"
            """.trimIndent()
        )

        val action = myFixture.availableIntentions.find { it.text == "BetterPy: Convert to f-string" }
        assertNull(action)
    }

    fun testNotAvailableWhenTString() {
        myFixture.configureByText(
            "test_fstring.py",
            """
            def greet(name):
                message = t"<caret>Hello {name}"
            """.trimIndent()
        )

        val action = myFixture.availableIntentions.find { it.text == "BetterPy: Convert to f-string" }
        assertNull(action)
    }

    fun testNotAvailableForNumericPlaceholder() {
        myFixture.configureByText(
            "test_numeric.py",
            """
            def greet():
                message = "<caret>Value {0}"
            """.trimIndent()
        )

        val action = myFixture.availableIntentions.find { it.text == "BetterPy: Convert to f-string" }
        assertNull(action)
    }

    fun testNotAvailableWhenDisabled() {
        PluginSettingsState.instance().state.enableConvertToFStringIntention = false
        try {
            myFixture.configureByText(
                "test_disabled.py",
                """
                def greet(name):
                    message = "<caret>Hello {name}"
                """.trimIndent()
            )

            val action = myFixture.availableIntentions.find { it.text == "BetterPy: Convert to f-string" }
            assertNull(action)
        } finally {
            PluginSettingsState.instance().state.enableConvertToFStringIntention = true
        }
    }
}
