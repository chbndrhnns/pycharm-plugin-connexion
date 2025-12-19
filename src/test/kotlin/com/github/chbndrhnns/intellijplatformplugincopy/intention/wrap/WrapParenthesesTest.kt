package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.doIntentionTest

/**
 * Parentheses and nesting related wrap tests.
 */
class WrapParenthesesTest : TestBase() {

    fun testWrapParenthesizedAssignment() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from pathlib import Path
            a: Path = ("<caret>val")
            """,
            """
            from pathlib import Path
            a: Path = Path("val")
            """,
            "BetterPy: Wrap with Path()"
        )
    }

    fun testWrapNestedParenthesizedAssignment() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from pathlib import Path
            a: Path = (("<caret>val"))
            """,
            """
            from pathlib import Path
            a: Path = Path("val")
            """,
            "BetterPy: Wrap with Path()"
        )
    }

    fun testWrapParenthesizedArgument() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value
            
            
            def process_data(data: CustomWrapper) -> str:
                return data.value
            
            
            result = process_data(<caret>((("some_string"))))
            """,
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value
            
            
            def process_data(data: CustomWrapper) -> str:
                return data.value
            
            
            result = process_data(CustomWrapper("some_string"))
            """,
            "BetterPy: Wrap with CustomWrapper()"
        )
    }
}