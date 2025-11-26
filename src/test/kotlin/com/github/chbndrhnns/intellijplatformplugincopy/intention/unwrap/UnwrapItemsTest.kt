package com.github.chbndrhnns.intellijplatformplugincopy.intention.unwrap

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class UnwrapItemsTest : TestBase() {

    fun testList_CustomInt_UnwrapItems() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[int] = [
                CustomInt(1),
                <caret>CustomInt(2),
                CustomInt(3),
            ]
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intentions = myFixture.filterAvailableIntentions("Unwrap")
        val names = intentions.map { it.text }

        println("Available intentions: \$names")

        assertContainsElements(names, "Unwrap CustomInt()")
        assertContainsElements(names, "Unwrap items CustomInt()")
    }

    fun testUnwrapItems_InvokesAllItemsUnwrapping() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[int] = [
                CustomInt(1),
                <caret>CustomInt(2),
                CustomInt(3),
            ]
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Unwrap items CustomInt()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class CustomInt(int):
                pass

            val: list[int] = [
                1,
                2,
                3,
            ]
            """.trimIndent()
        )
    }
}
