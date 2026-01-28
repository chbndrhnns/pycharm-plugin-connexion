package com.github.chbndrhnns.betterpy.features.intentions.populate

import fixtures.*

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
                "BetterPy: Populate arguments..."
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
                "BetterPy: Populate arguments..."
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
                "BetterPy: Populate arguments..."
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
                "BetterPy: Populate arguments..."
            )
        }
    }

    fun testRecursiveDataclassPopulation_IncludesInheritedFields() {
        withPopulatePopupSelection(index = 2) {
            myFixture.doIntentionTest(
                "a.py",
                """
            from dataclasses import dataclass

            @dataclass
            class Base:
                base: int

            @dataclass
            class Leaf:
                val: int

            @dataclass
            class Child(Base):
                leaf: Leaf

            c = Child(<caret>)
            """,
                """
            from dataclasses import dataclass

            @dataclass
            class Base:
                base: int

            @dataclass
            class Leaf:
                val: int

            @dataclass
            class Child(Base):
                leaf: Leaf

            c = Child(base=..., leaf=Leaf(val=...))

            """,
                "BetterPy: Populate arguments..."
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

    fun testUnionChooser_SelectsMember() {
        withPopulatePopupSelections(indices = listOf(2, 1)) { fake ->
            myFixture.doIntentionTest(
                "a.py",
                """
                from dataclasses import dataclass

                @dataclass
                class A:
                    a: int

                @dataclass
                class B:
                    b: int

                @dataclass
                class C:
                    v: A | B

                c = C(<caret>)
                """,
                """
                from dataclasses import dataclass

                @dataclass
                class A:
                    a: int

                @dataclass
                class B:
                    b: int

                @dataclass
                class C:
                    v: A | B

                c = C(v=B(b=...))

                """,
                "BetterPy: Populate arguments..."
            )

            assertEquals("Select union type for v", fake.lastTitle)
        }
    }
}
