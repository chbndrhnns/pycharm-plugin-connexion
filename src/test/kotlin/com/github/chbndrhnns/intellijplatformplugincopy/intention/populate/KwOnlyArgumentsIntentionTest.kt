package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest
import fixtures.withPopulatePopupSelection

class KwOnlyArgumentsIntentionTest : TestBase() {

    fun testDataclassPopulation() {
        withPopulatePopupSelection(index = 0) { // All arguments, non-recursive
            myFixture.doIntentionTest(
                "a.py",
                """
                from dataclasses import dataclass

                @dataclass
                class A:
                    x: int
                    y: int
                    z: int = 1

                a = A(<caret>)
                """,
                """
                from dataclasses import dataclass

                @dataclass
                class A:
                    x: int
                    y: int
                    z: int = 1

                a = A(x=..., y=..., z=...)
                """,
                "BetterPy: Populate arguments..."
            )
        }
    }

    fun testKwOnlyMethods() {
        withPopulatePopupSelection(index = 0) {
            myFixture.doIntentionTest(
                "a.py",
                """
                class C:
                    def foo(self, *, a, b):
                        pass

                C().foo(<caret>)
                """,
                """
                class C:
                    def foo(self, *, a, b):
                        pass

                C().foo(a=..., b=...)
                """,
                "BetterPy: Populate arguments..."
            )
        }
    }

    fun testNestedClass() {
        withPopulatePopupSelection(index = 0) {
            myFixture.doIntentionTest(
                "a.py",
                """
                from dataclasses import dataclass

                class Outer:
                    @dataclass
                    class Inner:
                        f: int

                Outer.Inner(<caret>)
                """,
                """
                from dataclasses import dataclass

                class Outer:
                    @dataclass
                    class Inner:
                        f: int

                Outer.Inner(f=...)
                """,
                "BetterPy: Populate arguments..."
            )
        }
    }

    fun testPartialArguments() {
        withPopulatePopupSelection(index = 0) {
            myFixture.doIntentionTest(
                "a.py",
                """
                def foo(a, b, c):
                    pass

                foo(1, <caret>)
                """,
                """
                def foo(a, b, c):
                    pass

                foo(1, b=..., c=...)
                """,
                "BetterPy: Populate arguments..."
            )
        }
    }

    fun testPositionalOnlyFunctionCall_NoPopulateOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def sleep(__secs, /):
                pass

            sleep(1<caret>)
            """,
            "Populate arguments"
        )
    }
}
