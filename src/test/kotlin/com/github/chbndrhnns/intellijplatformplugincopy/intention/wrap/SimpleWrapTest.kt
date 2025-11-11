package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

class SimpleWrapTest : TestBase() {

    fun testWrapPathInStr() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: str = Path(<caret>"val")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from pathlib import Path
            a: str = str(Path("val"))
            """.trimIndent()
        )
    }

    fun testWrapStrInPath() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: Path = "<caret>val"
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with Path()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from pathlib import Path
            a: Path = Path("val")
            """.trimIndent()
        )
    }

    fun testWrapReturnValue() {
        myFixture.configureByText(
            "a.py", """
            def do(val: float) -> str:
                return <caret>val
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def do(val: float) -> str:
                return str(val)
            """.trimIndent()
        )
    }

    fun testNoDefaultWrapOfArgument() {
        myFixture.configureByText(
            "a.py", """
            def process_data() -> int:
                return 1            
            
            result = process_data<caret>()
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.availableIntentions.find { it.text == "Wrap with str()" }
        assertNull("Intention 'Wrap with str()' should NOT be available", intention)
    }

    fun testWrapNoQuotedValue() {
        myFixture.configureByText(
            "a.py", """
            def process_data(arg: str) -> int:
                return int(1)
            
            result = process_data(1<caret>23)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def process_data(arg: str) -> int:
                return int(1)
            
            result = process_data("123")
            """.trimIndent()
        )
    }

    fun testWrapSetCallIntoListParam() {
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

    fun testWrapRangeCallIntoListParam() {
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

    fun testOfferBoolWrappingForKeywordOnlyParam() {
        myFixture.configureByText(
            "a.py",
            """
            class CompletedProcess: ...

            def run(*, shell: bool = False) -> CompletedProcess:
                return CompletedProcess()

            run(shell=<caret>0)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with bool()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class CompletedProcess: ...

            def run(*, shell: bool = False) -> CompletedProcess:
                return CompletedProcess()

            run(shell=bool(0))
            """.trimIndent()
        )
    }
}