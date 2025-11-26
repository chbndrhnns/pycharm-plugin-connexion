package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class WrapItemsTest : TestBase() {

    fun testList_CustomInt_WrapSingleElementAndWrapItems() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[CustomInt] = [
                1,
                <caret>2,
                3,
            ]
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intentions = myFixture.filterAvailableIntentions("Wrap")
        val names = intentions.map { it.text }

        assertContainsElements(names, "Wrap with CustomInt()")
        assertContainsElements(names, "Wrap items with CustomInt()")
    }

    fun testWrapSingleElement_InvokesSingleWrapping() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[CustomInt] = [
                1,
                <caret>2,
                3,
            ]
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with CustomInt()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class CustomInt(int):
                pass

            val: list[CustomInt] = [
                1,
                CustomInt(2),
                3,
            ]
            """.trimIndent()
        )
    }

    fun testWrapItems_InvokesAllItemsWrapping() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[CustomInt] = [
                1,
                <caret>2,
                3,
            ]
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap items with CustomInt()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class CustomInt(int):
                pass

            val: list[CustomInt] = [
                CustomInt(1),
                CustomInt(2),
                CustomInt(3),
            ]
            """.trimIndent()
        )
    }

    fun testCustomInt_UnwrapAvailable_WrapNotOffered() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[int] = [
            Custom<caret>Int(1),
            ] 
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val wrapIntention = myFixture.availableIntentions.find { it.text.startsWith("Wrap items with") }
        assertNull("Wrap intention should not be offered", wrapIntention)
    }



}
