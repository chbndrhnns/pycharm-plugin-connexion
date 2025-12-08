package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest
import fixtures.withPopulatePopupSelection

class RecursiveArgumentsIntentionTest : TestBase() {

    fun testRecursiveDataclassPopulation() {
        withPopulatePopupSelection(index = 2) {
            myFixture.doIntentionTest(
                "a.py",
                """
            from dataclasses import dataclass

            @dataclass
            class C:
                x: int | None
                z: int = 1

            @dataclass
            class B:
                c: C

            @dataclass
            class A:
                b: B

            a = A(<caret>)
            """,
                """
            from dataclasses import dataclass

            @dataclass
            class C:
                x: int | None
                z: int = 1

            @dataclass
            class B:
                c: C

            @dataclass
            class A:
                b: B

            a = A(b=B(c=C(x=..., z=...)))

            """,
                "Populate arguments..."
            )
        }
    }

    fun testNewTypeLeafPopulation() {
        withPopulatePopupSelection(index = 2) {
            myFixture.doIntentionTest(
                "a.py",
                """
            from dataclasses import dataclass
            from typing import NewType

            MyStr = NewType("MyStr", str)

            @dataclass
            class Outer:
                val: MyStr

            a = Outer(<caret>)
            """,
                """
            from dataclasses import dataclass
            from typing import NewType

            MyStr = NewType("MyStr", str)

            @dataclass
            class Outer:
                val: MyStr

            a = Outer(val=MyStr(...))

            """,
                "Populate arguments..."
            )
        }
    }

    fun testMixedTypesPopulation() {
        withPopulatePopupSelection(index = 2) {
            myFixture.doIntentionTest(
                "a.py",
                """
            from dataclasses import dataclass

            @dataclass
            class Leaf:
                val: int

            @dataclass
            class Node:
                leaf: Leaf
                other: str

            a = Node(<caret>)
            """,
                """
            from dataclasses import dataclass

            @dataclass
            class Leaf:
                val: int

            @dataclass
            class Node:
                leaf: Leaf
                other: str

            a = Node(leaf=Leaf(val=...), other=...)

            """,
                "Populate arguments..."
            )
        }
    }

    fun testUnionWithDataclass() {
        withPopulatePopupSelection(index = 2) {
            myFixture.doIntentionTest(
                "a.py",
                """
            from dataclasses import dataclass
            from typing import Union

            @dataclass
            class D:
                v: int

            @dataclass
            class E:
                d: Union[D, None]

            e = E(<caret>)
            """,
                """
            from dataclasses import dataclass
            from typing import Union

            @dataclass
            class D:
                v: int

            @dataclass
            class E:
                d: Union[D, None]

            e = E(d=D(v=...))

            """,
                "Populate arguments..."
            )
        }
    }

    fun testRecursionLimit() {
        withPopulatePopupSelection(index = 2) {
            myFixture.doIntentionTest(
                "a.py",
                """
            from dataclasses import dataclass

            @dataclass
            class Rec:
                r: 'Rec'

            a = Rec(<caret>)
            """,
                """
            from dataclasses import dataclass
            
            @dataclass
            class Rec:
                r: 'Rec'
            
            a = Rec(r=Rec(r=Rec(r=Rec(r=Rec(r=Rec(r=Rec(r=...)))))))

            """,
                "Populate arguments..."
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
