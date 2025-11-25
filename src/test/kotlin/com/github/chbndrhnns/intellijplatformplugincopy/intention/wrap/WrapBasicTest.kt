package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

/**
 * Basic wrap intention behavior: assignments, arguments, returns without
 * collections, unions, forward refs or dataclasses.
 */
class WrapBasicTest : TestBase() {

    fun testAssignment_PathToStr_WrapsWithStrConstructor() {
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

    fun testCustomInt_UnwrapAvailable_WrapNotOffered() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomInt(int):
                pass

            val: list[int] = [
            CustomInt(<caret>1),
            ] 
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val wrapIntention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with") }
        assertNull("Wrap intention should not be offered", wrapIntention)
    }

    fun testAssignment_StrToPath_WrapsWithPathConstructor() {
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

    fun testReturn_FloatToStr_WrapsWithStrConstructor() {
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

    fun testCall_NoExpectedType_NoStrWrapOffered() {
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

    fun testArgument_IntLiteral_ReplacedWithStrLiteral() {
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

    fun testKeywordOnlyParam_IntToBool_WrapsWithBoolConstructor() {
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

    fun testClassInit_ParseKwArg_NoWrapSuggestion() {
        // This tests that we do not fall back on class variables when suggesting wraps
        myFixture.configureByText(
            "a.py",
            """
            class Client:
                version: int
            
                def __init__(self, val: str) -> None:
                    self.val = val
            
            
            Client(val="a<caret>bc")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.availableIntentions.find { it.text.startsWith("Wrap with") }
        assertNull("Wrap intention should not be offered when types match, but got: ${intention?.text}", intention)
    }
}
