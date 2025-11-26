package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase

class KwOnlyArgumentsIntentionTest : TestBase() {

    fun testDataclassPopulation() {
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
        val intention = myFixture.findSingleIntention("Populate missing arguments with '...'")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from dataclasses import dataclass

            @dataclass
            class A:
                x: int
                y: int
                z: int = 1

            a = A(x=..., y=..., z=...)
            """.trimIndent()
        )
    }

    fun testKwOnlyMethods() {
        myFixture.configureByText(
            "a.py",
            """
            class C:
                def foo(self, *, a, b):
                    pass

            C().foo(<caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate missing arguments with '...'")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class C:
                def foo(self, *, a, b):
                    pass

            C().foo(a=..., b=...)
            """.trimIndent()
        )
    }

    fun testNestedClass() {
        myFixture.configureByText(
            "a.py",
            """
            from dataclasses import dataclass

            class Outer:
                @dataclass
                class Inner:
                    f: int

            Outer.Inner(<caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate missing arguments with '...'")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from dataclasses import dataclass

            class Outer:
                @dataclass
                class Inner:
                    f: int

            Outer.Inner(f=...)
            """.trimIndent()
        )
    }

    fun testPartialArguments() {
        myFixture.configureByText(
            "a.py",
            """
            def foo(a, b, c):
                pass

            foo(1, <caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate missing arguments with '...'")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def foo(a, b, c):
                pass

            foo(1, b=..., c=...)
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
        val intention = myFixture.availableIntentions.find { it.text == "Populate missing arguments with '...'" }
        assertNull("Populate intention should NOT be available for positional-only calls", intention)
    }
}
