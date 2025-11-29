package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class RequiredArgumentsIntentionTest : TestBase() {

    fun testDataclassPopulation_OnlyRequiredFields() {
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
            "Populate required arguments with '...'"
        )
    }

    fun testKwOnlyMethods_RequiredOnly() {
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
            "Populate required arguments with '...'"
        )
    }

    fun testNestedClass_RequiredOnly() {
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
            "Populate required arguments with '...'"
        )
    }

    fun testPartialArguments_OnlyMissingRequired() {
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
            "Populate required arguments with '...'"
        )
    }

    fun testPositionalOnlyFunctionCall_NoPopulateOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def sleep(__secs, /):
                pass

            sleep(1<caret>)
            """,
            "Populate required arguments with '...'"
        )
    }
}
