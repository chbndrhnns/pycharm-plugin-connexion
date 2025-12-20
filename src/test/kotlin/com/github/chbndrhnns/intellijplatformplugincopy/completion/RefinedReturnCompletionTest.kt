package com.github.chbndrhnns.intellijplatformplugincopy.completion

import fixtures.TestBase

class RefinedReturnCompletionTest : TestBase() {

    fun testContainerSuggestions() {
        myFixture.configureByText(
            "test_container.py", """
            class MyStr(str):
                pass

            def do() -> list[MyStr]:
                return <caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_container.py")
        assertNotNull(variants)
        // Current behavior is "list", "MyStr" (maybe?).
        // We want "[MyStr()]".
        // Note: completion variants usually return the lookup string.
        // If I change the lookup string to "[MyStr()]", then it should appear in variants.

        assertTrue("Should contain '[MyStr()]', found: $variants", variants!!.contains("[MyStr()]"))
    }

    fun testSetContainerSuggestion() {
        myFixture.configureByText(
            "test_set.py", """
            class MyStr(str):
                pass

            def do() -> set[MyStr]:
                return <caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_set.py")
        assertNotNull(variants)
        assertTrue("Should contain '{MyStr()}', found: $variants", variants!!.contains("{MyStr()}"))
    }

    fun testTupleContainerSuggestion() {
        myFixture.configureByText(
            "test_tuple.py", """
            class MyStr(str):
                pass

            def do() -> tuple[MyStr]:
                return <caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_tuple.py")
        assertNotNull(variants)

        assertTrue(
            "Should contain '(MyStr())' or '(MyStr(),)', found: $variants",
            variants!!.any { it == "(MyStr())" || it == "(MyStr(),)" }
        )
    }
}
