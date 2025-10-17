package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.MyPlatformTestCase
import com.jetbrains.python.inspections.PyTypeCheckerInspection

internal class TypeMismatchQuickFixIntentionTest : MyPlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
    }

    fun testQuickfixIsAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: str = Path(<caret>"val")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        myFixture.findSingleIntention("Show type mismatch details")
    }

    fun testWrapIntentionIsAvailable() {
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
        assertNotNull("Wrap intention should be available", wrapIntention)
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
}
