package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

/**
 * Legacy list-related wrap tests.
 *
 * New collection scenarios should go into `WrapCollectionsTest`.
 */
class WrapCollectionsTest : TestBase() {

    fun testList_StringLiteral_WrapsIntoSingleElement() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def do(l: list[str]):
                return l

            def test_():
                do(<caret>"abc")
            """,
            """
            def do(l: list[str]):
                return l

            def test_():
                do(["abc"])
            """,
            "BetterPy: Wrap with list()"
        )
    }

    fun testList_IntLiteral_WrapsIntoSingleElement() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def do(l: list[int]):
                return l

            def test_():
                do(<caret>123)
            """,
            """
            def do(l: list[int]):
                return l

            def test_():
                do([123])
            """,
            "BetterPy: Wrap with list()"
        )
    }

    fun testList_TupleValue_WrapsUsingListConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def do(l: list[int]):
                return l

            def test_():
                do(<caret>(1, 2))
            """,
            """
            def do(l: list[int]):
                return l

            def test_():
                do(list((1, 2)))
            """,
            "BetterPy: Wrap with list()"
        )
    }

    fun testListParam_SetCall_WrapsWithListConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def do(arg: list[str]):
                return arg

            do(s<caret>et())
            """,
            """
            def do(arg: list[str]):
                return arg

            do(list(set()))
            """,
            "BetterPy: Wrap with list()"
        )
    }

    fun testListParam_RangeCall_WrapsWithListConstructor() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def do(arg: list[int]):
                return arg

            do(r<caret>ange(3))
            """,
            """
            def do(arg: list[int]):
                return arg

            do(list(range(3)))
            """,
            "BetterPy: Wrap with list()"
        )
    }

    fun testDoNotOfferNoneWrappingInNestedListCall() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def do(arg: list[str]):
                return arg

            do(l<caret>ist(0))
            """,
            "BetterPy: Wrap with None()"
        )
    }

    fun testWrapListElementWithNewTypeInAnnotatedAssignment() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            val: list[CloudId] = [<caret>"abc"]
            """,
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            val: list[CloudId] = [CloudId("abc")]
            """,
            "BetterPy: Wrap with CloudId()"
        )
    }

    fun testWrapListElementWithNewTypeInFunctionArg() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            def do(arg: list[CloudId]) -> None:
                pass

            do([<caret>"abc"])            
            """,
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            def do(arg: list[CloudId]) -> None:
                pass

            do([CloudId("abc")])            
            """,
            "BetterPy: Wrap with CloudId()"
        )
    }

    fun testWrapDictKeyAndValue() {
        // Key wrapping: expect int() for a str literal into Dict[int, int]
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import Dict
            val: Dict[int, int] = {<caret>"k": 1}
            """,
            """
            from typing import Dict
            val: Dict[int, int] = {int("k"): 1}
            """,
            "BetterPy: Wrap with int()"
        )

        // Value wrapping
        myFixture.doIntentionTest(
            "b.py",
            """
            from typing import Dict
            val: Dict[str, int] = {"k": <caret>True}
            """,
            """
            from typing import Dict
            val: Dict[str, int] = {"k": int(True)}
            """,
            "BetterPy: Wrap with int()"
        )
    }

    fun testDoNotOfferListWrappingInsideListLiteral_OneElement() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(arg: list[int]):
                pass

            f([<caret>1])
            """,
            "BetterPy: Wrap with list()"
        )
    }

    fun testDoNotOfferListWrappingForList() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(arg: list[int]):
                pass

            f([<caret>1, "3"])
            """,
            "BetterPy: Wrap with list()"
        )
    }

    fun testDoNotOfferListWrappingInsideListLiteral__MultipleElements() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def do(arg: list[int]):
                return arg
            
            
            do([1, <caret>2, 3, "3"])
            """,
            "BetterPy: Wrap with list()"
        )
    }

    fun testDoNotOfferListWrappingInsideListLiteral__MultipleElements2() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def do(arg: list[int]):
                return arg
            
            
            do([1,<caret> 2, 3, "3"])
            """,
            "BetterPy: Wrap with list()"
        )
    }

    fun testDoNotOfferWrappingInsideListLiteralIfCorrectType() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            val: list[int] = [1, 2, <caret>3, "3"]
            """,
            "BetterPy: Wrap with int()"
        )
    }

    fun testDoNotOfferSetWrappingInsideSetLiteral_OneElement() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(arg: set[int]):
                pass

            f({<caret>1})
            """,
            "BetterPy: Wrap with set()"
        )
    }

    fun testWrapTupleElementInAnnotatedAssignment() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            t: tuple[CloudId, int] = [<caret>"abc", 2]
            """,
            """
            from typing import NewType
            CloudId = NewType("CloudId", str)

            t: tuple[CloudId, int] = [CloudId("abc"), 2]
            """,
            "BetterPy: Wrap with CloudId()"
        )
    }

    fun testWrapDictKeyWithNewTypeInAnnotatedAssignment() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import NewType
            Key = NewType("Key", int)
            Val = NewType("Val", str)

            m: dict[Key, Val] = {<caret>1: "a"}
            """,
            """
            from typing import NewType
            Key = NewType("Key", int)
            Val = NewType("Val", str)

            m: dict[Key, Val] = {Key(1): "a"}
            """,
            "BetterPy: Wrap with Key()"
        )
    }

    // Phase 1 fixes - Issue 1.2: Set literal vs constructor
    // Wrap should use set literal {T} instead of set(T) for single elements
    fun testSet_SingleElement_WrapsWithSetLiteral() {
        myFixture.doIntentionTest(
            "a.py", """
            def do(s: set[str]):
                return s

            def test_():
                do(<caret>"abc")
            """, """
            def do(s: set[str]):
                return s

            def test_():
                do({"abc"})
            """, "BetterPy: Wrap with set()"
        )
    }

    // Phase 1 fixes - Issue 1.3: Wrap suggests set instead of element type
    // For vals: set[str] = {1}, should suggest str(1) for the element, not set wrapping
    fun testSet_ElementTypeMismatch_WrapsElementNotContainer() {
        myFixture.doIntentionTest(
            "a.py", """
            vals: set[str] = {<caret>1}
            """, """
            vals: set[str] = {"1"}
            """, "BetterPy: Wrap with str()"
        )
    }

    // Phase 1 fixes - Issue 1.1: Inner problem priority
    // When nested constructors have type mismatches, wrap should offer inner element wrapping first
    // Simplified test: just test that wrap works for a function argument with stdlib type
    fun testFunctionArg_StdlibType_WrapsWithExpectedType() {
        myFixture.doIntentionTest(
            "a.py", """
            from ipaddress import IPv4Interface
            
            def process(prefix: IPv4Interface): ...
            
            process(<caret>"10.10.10.0/24")
            """, """
            from ipaddress import IPv4Interface
            
            def process(prefix: IPv4Interface): ...
            
            process(IPv4Interface("10.10.10.0/24"))
            """, "BetterPy: Wrap with IPv4Interface()"
        )
    }

    // Test nested context: class constructor inside dict literal
    // TODO: This test is deferred - requires deeper investigation into ExpectedTypeInfo resolution
    fun _testNestedConstructor_InsideDict_WrapsInnerArgument() {
        myFixture.doIntentionTest(
            "a.py", """
            from ipaddress import IPv4Interface
            
            class Prefix:
                def __init__(self, prefix: IPv4Interface): ...
            
            prefixes: dict[int, Prefix] = {
                1: Prefix(prefix=<caret>"10.10.10.0/24")
            }
            """, """
            from ipaddress import IPv4Interface
            
            class Prefix:
                def __init__(self, prefix: IPv4Interface): ...
            
            prefixes: dict[int, Prefix] = {
                1: Prefix(prefix=IPv4Interface("10.10.10.0/24"))
            }
            """, "BetterPy: Wrap with IPv4Interface()"
        )
    }
}
