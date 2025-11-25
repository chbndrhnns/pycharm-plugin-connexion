package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class PopulateKwOnlyArgumentsIntentionTest : TestBase() {

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
}
