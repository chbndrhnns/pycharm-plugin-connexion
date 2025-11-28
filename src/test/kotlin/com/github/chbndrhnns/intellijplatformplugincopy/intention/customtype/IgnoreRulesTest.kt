package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase

class IgnoreRulesTest : TestBase() {

    fun testIntentionNotOfferedInsideDunderAllValues() {
        myFixture.configureByText(
            "a.py",
            """
            __all__ = ["i<caret>nt"]
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intentions = myFixture.availableIntentions
        val hasIntroduce = intentions.any { it.text.startsWith("Introduce custom type") }
        assertFalse("Intention should not be offered for __all__ assignment values", hasIntroduce)
    }

    fun testIntentionNotOfferedForIgnoredSymbolNames() {
        myFixture.configureByText(
            "a.py",
            """
            __version__: int = 1<caret>23
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intentions = myFixture.availableIntentions
        val hasIntroduce = intentions.any { it.text.startsWith("Introduce custom type") }
        assertFalse("Intention should not be offered for ignored symbol names like __version__", hasIntroduce)
    }
    
    fun testIntentionStillOfferedForRegularSymbol() {
        myFixture.configureByText(
            "a.py",
            """
            def f():
                value: int = 1<caret>23
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intentions = myFixture.availableIntentions
        val hasIntroduce = intentions.any { it.text.startsWith("Introduce custom type") }
        assertTrue("Intention should still be offered for regular symbols", hasIntroduce)
    }
}
