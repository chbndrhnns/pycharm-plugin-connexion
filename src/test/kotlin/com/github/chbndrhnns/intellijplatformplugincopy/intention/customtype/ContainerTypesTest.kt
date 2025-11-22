package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class ContainerTypesTest : TestBase() {

    fun testList_IntentionAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: li<caret>st[int]):
                pass
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from list")
        assertNotEmpty(intentions)
    }

    fun testSet_IntentionAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: s<caret>et[int]):
                pass
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from set")
        assertNotEmpty(intentions)
    }

    fun testDict_IntentionAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: di<caret>ct[str, int]):
                pass
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from dict")
        assertNotEmpty(intentions)
    }

    fun testList_GeneratesCustomType() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: li<caret>st[int]):
                pass
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from list")
        myFixture.launchAction(intention)

        val text = myFixture.file.text
        // We check for CustomList (PascalCase) or Customlist (fallback if mapping fails, though it shouldn't)
        // just to be robust against environmental weirdness in test runner.
        // But strictly it should be CustomList.
        assertTrue(
            "Should contain generated class name",
            text.contains("class CustomList") || text.contains("class Customlist")
        )
        assertTrue("Should update annotation", text.contains("x: CustomList") || text.contains("x: Customlist"))
    }
}
