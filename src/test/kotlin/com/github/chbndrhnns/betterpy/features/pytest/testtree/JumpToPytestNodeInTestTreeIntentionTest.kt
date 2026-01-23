package com.github.chbndrhnns.betterpy.features.pytest.testtree

import fixtures.TestBase

class JumpToPytestNodeInTestTreeIntentionTest : TestBase() {

    fun testIsHiddenWhenNoActiveTestTree() {
        myFixture.configureByText(
            "test_file.py", """
            def test_something():
                <caret>pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Jump to Test Tree Node")
        assertEmpty("Intention should be hidden when no active test tree", intentions)
    }

    fun testIsHiddenInNonTestContext() {
        myFixture.configureByText(
            "some_file.py", """
            def some_normal_function():
                <caret>pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Jump to Test Tree Node")
        assertEmpty("Intention should be hidden in non-test context", intentions)
    }
}
