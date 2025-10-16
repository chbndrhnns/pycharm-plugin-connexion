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
}
