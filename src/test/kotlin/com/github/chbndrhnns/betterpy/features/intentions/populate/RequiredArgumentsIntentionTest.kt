package com.github.chbndrhnns.betterpy.features.intentions.populate

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest
import fixtures.withPopulatePopupSelection

class RequiredArgumentsIntentionTest : TestBase() {

    fun testDataclassPopulation_OnlyRequiredFields() {
        withPopulatePopupSelection(index = 1) { // Required only, non-recursive
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

                a = A(x=..., y=...)
                """,
                "BetterPy: Populate arguments..."
            )
        }
    }

    fun testKwOnlyMethods_RequiredOnly() {
        withPopulatePopupSelection(index = 1) {
            myFixture.doIntentionTest(
                "a.py",
                """
                class C:
                    def foo(self, *, a, b=1):
                        pass

                C().foo(<caret>)
                """,
                """
                class C:
                    def foo(self, *, a, b=1):
                        pass

                C().foo(a=...)
                """,
                "BetterPy: Populate arguments..."
            )
        }
    }

    fun testNestedClass_RequiredOnly() {
        withPopulatePopupSelection(index = 1) {
            myFixture.doIntentionTest(
                "a.py",
                """
                from dataclasses import dataclass

                class Outer:
                    @dataclass
                    class Inner:
                        f: int
                        g: int | None = None

                Outer.Inner(<caret>)
                """,
                """
                from dataclasses import dataclass

                class Outer:
                    @dataclass
                    class Inner:
                        f: int
                        g: int | None = None

                Outer.Inner(f=...)
                """,
                "BetterPy: Populate arguments..."
            )
        }
    }

    fun testPartialArguments_OnlyMissingRequired() {
        withPopulatePopupSelection(index = 1) {
            myFixture.doIntentionTest(
                "a.py",
                """
                def foo(a, b, c=3):
                    pass

                foo(1, <caret>)
                """,
                """
                def foo(a, b, c=3):
                    pass

                foo(1, b=...)
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
