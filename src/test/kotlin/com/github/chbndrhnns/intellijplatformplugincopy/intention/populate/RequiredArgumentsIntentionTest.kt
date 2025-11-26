package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase

class RequiredArgumentsIntentionTest : TestBase() {

    fun testDataclassPopulation_OnlyRequiredFields() {
        myFixture.configureByText(
            "a.py",
            """
            from dataclasses import dataclass

            @dataclass
            class A:
                x: int
                y: int
                z: int = 1

            a = A(<caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate required arguments with '...'")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from dataclasses import dataclass

            @dataclass
            class A:
                x: int
                y: int
                z: int = 1

            a = A(x=..., y=...)
            """.trimIndent()
        )
    }

    fun testKwOnlyMethods_RequiredOnly() {
        myFixture.configureByText(
            "a.py",
            """
            class C:
                def foo(self, *, a, b=1):
                    pass

            C().foo(<caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate required arguments with '...'")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class C:
                def foo(self, *, a, b=1):
                    pass

            C().foo(a=...)
            """.trimIndent()
        )
    }

    fun testNestedClass_RequiredOnly() {
        myFixture.configureByText(
            "a.py",
            """
            from dataclasses import dataclass

            class Outer:
                @dataclass
                class Inner:
                    f: int
                    g: int | None = None

            Outer.Inner(<caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate required arguments with '...'")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from dataclasses import dataclass

            class Outer:
                @dataclass
                class Inner:
                    f: int
                    g: int | None = None

            Outer.Inner(f=...)
            """.trimIndent()
        )
    }

    fun testPartialArguments_OnlyMissingRequired() {
        myFixture.configureByText(
            "a.py",
            """
            def foo(a, b, c=3):
                pass

            foo(1, <caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate required arguments with '...'")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def foo(a, b, c=3):
                pass

            foo(1, b=...)
            """.trimIndent()
        )
    }

    fun testPositionalOnlyFunctionCall_NoPopulateOffered() {
        myFixture.configureByText(
            "a.py",
            """
            def sleep(__secs, /):
                pass

            sleep(1<caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.availableIntentions.find { it.text == "Populate required arguments with '...'" }
        assertNull("Populate intention should NOT be available for positional-only calls", intention)
    }
}
