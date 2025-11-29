package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.assertIntentionAvailable
import fixtures.withWrapPopupSelection

/**
 * UI / presentation tests for wrap intention (non-forward-ref cases):
 * - intention preview contents
 * - intention text and chooser labels
 */
class WrapPresentationTest : TestBase() {

    fun testWrapIntentionPreviewShowsActualCode() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: str = Path(<caret>"val")
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val intention = myFixture.findSingleIntention("Wrap with str()")
        val previewText = myFixture.getIntentionPreviewText(intention)
        assertEquals("str(Path(\"val\"))", previewText)
    }

    fun testPreviewShowsImportLineWhenNeeded() {
        myFixture.addFileToProject(
            "b.py",
            """
            class B:
                def __init__(self, value: str):
                    self.value = value

            def consume(x: B) -> None:
                ...
            """.trimIndent()
        )
        myFixture.configureByText(
            "a.py",
            """
            from b import consume

            consume(<caret>"val")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with B()")
        val previewText = myFixture.getIntentionPreviewText(intention)
        assertEquals("from b import B\nB(\"val\")", previewText)
    }

    fun testWrapIntentionTextUsesActualType() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            from pathlib import Path  
            a: str = Path(<caret>"val")
            """,
            "Wrap with str()"
        )
    }

    fun testUnionChooserShowsFqnForClasses() {
        withWrapPopupSelection(0) { fake ->
            myFixture.configureByText(
                "a.py",
                """
                class User:
                    def __init__(self, value: str):
                        self.value = value

                class Token:
                    def __init__(self, value: str):
                        self.value = value

                def f(x: User | Token) -> None:
                    ...

                f(<caret>"abc")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            assertEquals(listOf("User (a.User)", "Token (a.Token)"), fake.lastLabels)
        }
    }
}
