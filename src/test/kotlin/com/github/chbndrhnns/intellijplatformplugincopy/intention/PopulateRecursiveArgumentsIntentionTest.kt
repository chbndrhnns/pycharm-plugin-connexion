package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class PopulateRecursiveArgumentsIntentionTest : TestBase() {

    fun testRecursiveDataclassPopulation() {
        myFixture.configureByText(
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
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate missing arguments recursively")
        myFixture.launchAction(intention)

        myFixture.checkResult(
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

            """.trimIndent()
        )
    }

    fun testMixedTypesPopulation() {
        myFixture.configureByText(
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
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate missing arguments recursively")
        myFixture.launchAction(intention)

        println("DEBUG_RESULT_2:\n" + myFixture.file.text)

        myFixture.checkResult(
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

            """.trimIndent()
        )
    }

    fun testUnionWithDataclass() {
        myFixture.configureByText(
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
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate missing arguments recursively")
        myFixture.launchAction(intention)

        println("DEBUG_RESULT_3:\n" + myFixture.file.text)

        myFixture.checkResult(
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

            """.trimIndent()
        )
    }

    fun testRecursionLimit() {
        myFixture.configureByText(
            "a.py",
            """
            from dataclasses import dataclass

            @dataclass
            class Rec:
                r: 'Rec'

            a = Rec(<caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate missing arguments recursively")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from dataclasses import dataclass
            
            @dataclass
            class Rec:
                r: 'Rec'
            
            a = Rec(r=Rec(r=Rec(r=Rec(r=Rec(r=Rec(r=Rec(r=...)))))))

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
        val intention = myFixture.availableIntentions.find { it.text == "Populate missing arguments recursively" }
        assertNull("Populate recursive intention should NOT be available for positional-only calls", intention)
    }
}
