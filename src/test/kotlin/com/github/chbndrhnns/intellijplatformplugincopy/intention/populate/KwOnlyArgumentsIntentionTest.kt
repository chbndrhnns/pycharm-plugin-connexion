package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class KwOnlyArgumentsIntentionTest : TestBase() {

    fun testDataclassPopulation() {
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
            "Populate missing arguments with '...'"
        )
    }

    fun testKwOnlyMethods() {
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
            "Populate missing arguments with '...'"
        )
    }

    fun testNestedClass() {
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
            "Populate missing arguments with '...'"
        )
    }

    fun testPartialArguments() {
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
            "Populate missing arguments with '...'"
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
            "Populate missing arguments with '...'"
        )
    }
}
