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
}