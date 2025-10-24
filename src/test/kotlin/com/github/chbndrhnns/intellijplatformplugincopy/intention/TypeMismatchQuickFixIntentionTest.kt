package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.MyPlatformTestCase
import com.jetbrains.python.inspections.PyTypeCheckerInspection

internal class TypeMismatchQuickFixIntentionTest : MyPlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
    }

    fun testWrapInExpectedType() {
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

    fun testWrapIntentionTextUsesActualType() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path  
            a: str = Path(<caret>"val")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intentions = myFixture.availableIntentions
        val wrapIntention = intentions.find { it.text.startsWith("Wrap with") }

        // Verify that the intention text shows the actual expected type
        assertNotNull("Wrap intention should be available", wrapIntention)
        assertEquals("Intention text should show actual expected type", "Wrap with str()", wrapIntention?.text)
    }
}
