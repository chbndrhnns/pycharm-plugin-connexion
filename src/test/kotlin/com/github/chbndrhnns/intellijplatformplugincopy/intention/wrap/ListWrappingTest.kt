package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

/**
 * Legacy list-related wrap tests.
 *
 * New collection scenarios should go into `WrapCollectionsTest`.
 */
class WrapCollectionsTest : TestBase() {

    fun testList_StringLiteral_WrapsIntoSingleElement() {
        myFixture.configureByText(
            "a.py",
            """
            def do(l: list[str]):
                return l

            def test_():
                do(<caret>"abc")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with list()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def do(l: list[str]):
                return l

            def test_():
                do(["abc"])
            """.trimIndent()
        )
    }

    fun testList_IntLiteral_WrapsIntoSingleElement() {
        myFixture.configureByText(
            "a.py",
            """
            def do(l: list[int]):
                return l

            def test_():
                do(<caret>123)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with list()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def do(l: list[int]):
                return l

            def test_():
                do([123])
            """.trimIndent()
        )
    }

    fun testList_TupleValue_WrapsUsingListConstructor() {
        myFixture.configureByText(
            "a.py",
            """
            def do(l: list[int]):
                return l

            def test_():
                do(<caret>(1, 2))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with list()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def do(l: list[int]):
                return l

            def test_():
                do(list((1, 2)))
            """.trimIndent()
        )
    }

    fun testListParam_SetCall_WrapsWithListConstructor() {
        myFixture.configureByText(
            "a.py",
            """
            def do(arg: list[str]):
                return arg

            do(s<caret>et())
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with list()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def do(arg: list[str]):
                return arg

            do(list(set()))
            """.trimIndent()
        )
    }

    fun testListParam_RangeCall_WrapsWithListConstructor() {
        myFixture.configureByText(
            "a.py",
            """
            def do(arg: list[int]):
                return arg

            do(r<caret>ange(3))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with list()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def do(arg: list[int]):
                return arg

            do(list(range(3)))
            """.trimIndent()
        )
    }

    fun testDoNotOfferNoneWrappingInNestedListCall() {
        myFixture.configureByText(
            "a.py",
            """
            def do(arg: list[str]):
                return arg

            do(l<caret>ist(0))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val noneIntention = myFixture.availableIntentions.find { it.text == "Wrap with None()" }
        assertNull("Intention 'Wrap with None()' must NOT be available", noneIntention)
    }

    fun testWrapListElementWithNewTypeInAnnotatedAssignment() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            val: list[CloudId] = [<caret>"abc"]
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with CloudId()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            val: list[CloudId] = [CloudId("abc")]
            """.trimIndent()
        )
    }

    fun testWrapListElementWithNewTypeInFunctionArg() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            def do(arg: list[CloudId]) -> None:
                pass

            do([<caret>"abc"])            
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with CloudId()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            def do(arg: list[CloudId]) -> None:
                pass

            do([CloudId("abc")])            
            """.trimIndent()
        )
    }

    fun testWrapDictKeyAndValue() {
        // Key wrapping: expect int() for a str literal into Dict[int, int]
        myFixture.configureByText(
            "a.py",
            """
            from typing import Dict
            val: Dict[int, int] = {<caret>"k": 1}
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val keyIntent = myFixture.findSingleIntention("Wrap with int()")
        myFixture.launchAction(keyIntent)
        myFixture.checkResult(
            """
            from typing import Dict
            val: Dict[int, int] = {int("k"): 1}
            """.trimIndent()
        )

        // Value wrapping
        myFixture.configureByText(
            "b.py",
            """
            from typing import Dict
            val: Dict[str, int] = {"k": <caret>True}
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val valIntent = myFixture.findSingleIntention("Wrap with int()")
        myFixture.launchAction(valIntent)
        myFixture.checkResult(
            """
            from typing import Dict
            val: Dict[str, int] = {"k": int(True)}
            """.trimIndent()
        )
    }

    fun testDoNotOfferListWrappingInsideListLiteral_OneElement() {
        myFixture.configureByText(
            "a.py",
            """
            def f(arg: list[int]):
                pass

            f([<caret>1])
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val listIntention = myFixture.availableIntentions.find { it.text == "Wrap with list()" }
        assertNull("Intention 'Wrap with list()' should NOT be available inside list literal", listIntention)
    }

    fun _testDoNotOfferListWrappingForList() {
        myFixture.configureByText(
            "a.py",
            """
            def f(arg: list[int]):
                pass

            f([<caret>1, "3"])
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val listIntention = myFixture.availableIntentions.find { it.text == "Wrap with list()" }
        assertNull("Intention 'Wrap with list()' should NOT be available inside list literal", listIntention)
    }

    fun testDoNotOfferListWrappingInsideListLiteral__MultipleElements() {
        myFixture.configureByText(
            "a.py",
            """
            def do(arg: list[int]):
                return arg
            
            
            do([1, <caret>2, 3, "3"])
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val listIntention = myFixture.availableIntentions.find { it.text == "Wrap with list()" }
        assertNull("Intention 'Wrap with list()' should NOT be available inside list literal", listIntention)
    }

    fun testDoNotOfferListWrappingInsideListLiteral__MultipleElements2() {
        myFixture.configureByText(
            "a.py",
            """
            def do(arg: list[int]):
                return arg
            
            
            do([1,<caret> 2, 3, "3"])
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val listIntention = myFixture.availableIntentions.find { it.text == "Wrap with list()" }
        assertNull("Intention 'Wrap with list()' should NOT be available inside list literal", listIntention)
    }

    fun testDoNotOfferWrappingInsideListLiteralIfCorrectType() {
        myFixture.configureByText(
            "a.py",
            """
            val: list[int] = [1, 2, <caret>3, "3"]
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val listIntention = myFixture.availableIntentions.find { it.text == "Wrap with int()" }
        assertNull("Intention 'Wrap with int()' should NOT be available inside list literal", listIntention)
    }

    fun testDoNotOfferSetWrappingInsideSetLiteral_OneElement() {
        myFixture.configureByText(
            "a.py",
            """
            def f(arg: set[int]):
                pass

            f({<caret>1})
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val setIntention = myFixture.availableIntentions.find { it.text == "Wrap with set()" }
        assertNull("Intention 'Wrap with set()' should NOT be available inside set literal", setIntention)
    }

    fun testWrapTupleElementInAnnotatedAssignment() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            t: tuple[CloudId, int] = [<caret>"abc", 2]
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with CloudId()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            t: tuple[CloudId, int] = [CloudId("abc"), 2]
            """.trimIndent()
        )
    }

    fun testWrapDictKeyWithNewTypeInAnnotatedAssignment() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            Key = NewType("Key", int)
            Val = NewType("Val", str)

            m: dict[Key, Val] = {<caret>1: "a"}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with Key()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import NewType
            Key = NewType("Key", int)
            Val = NewType("Val", str)

            m: dict[Key, Val] = {Key(1): "a"}
            """.trimIndent()
        )
    }
}
