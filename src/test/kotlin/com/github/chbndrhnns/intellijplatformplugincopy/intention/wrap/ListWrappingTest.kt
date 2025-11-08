package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

class ListWrappingTest : TestBase() {

    fun testWrapStringIntoListProducesSingleElement() {
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

    fun testWrapIntIntoListUsesLiteral() {
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

    fun testWrapTupleIntoListUsesConstructor() {
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

        // Debug actual text to diagnose formatting differences if the comparison fails
        println("[DEBUG_LOG] ACTUAL RESULT\\n" + myFixture.file.text)

        myFixture.checkResult(
            """
            def do(l: list[int]):
                return l

            def test_():
                do(list((1, 2)))
            """.trimIndent()
        )
    }
}
