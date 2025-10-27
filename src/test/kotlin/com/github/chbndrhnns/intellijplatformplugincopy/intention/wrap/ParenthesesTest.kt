package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

class ParenthesesTest : TestBase() {

    fun testWrapParenthesizedAssignment() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: Path = ("<caret>val")
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

    fun testWrapNestedParenthesizedAssignment() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: Path = (("<caret>val"))
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

    fun testWrapParenthesizedArgument() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value
            
            
            def process_data(data: CustomWrapper) -> str:
                return data.value
            
            
            result = process_data(<caret>((("some_string"))))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with CustomWrapper()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value
            
            
            def process_data(data: CustomWrapper) -> str:
                return data.value
            
            
            result = process_data(CustomWrapper("some_string"))
            """.trimIndent()
        )
    }
}