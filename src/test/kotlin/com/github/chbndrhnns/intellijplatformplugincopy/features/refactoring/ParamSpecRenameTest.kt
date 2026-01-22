package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring

import fixtures.TestBase

class ParamSpecRenameTest : TestBase() {

    fun testRenameParamSpecFirstArgument() {
        myFixture.configureByText(
            "test.py", """
            from typing import ParamSpec
            
            P = ParamSpec("<caret>P")
            
            def call_it(f: Callable[P, int]) -> int:
                pass
        """.trimIndent()
        )

        myFixture.renameElementAtCaret("Q")

        myFixture.checkResult(
            """
            from typing import ParamSpec
            
            Q = ParamSpec("Q")
            
            def call_it(f: Callable[Q, int]) -> int:
                pass
        """.trimIndent()
        )
    }

    fun testRenameParamSpecVariable() {
        myFixture.configureByText(
            "test.py", """
            from typing import ParamSpec
            
            <caret>P = ParamSpec("P")
            
            def call_it(f: Callable[P, int]) -> int:
                pass
        """.trimIndent()
        )

        myFixture.renameElementAtCaret("Q")

        myFixture.checkResult(
            """
            from typing import ParamSpec
            
            Q = ParamSpec("Q")
            
            def call_it(f: Callable[Q, int]) -> int:
                pass
        """.trimIndent()
        )
    }
}
