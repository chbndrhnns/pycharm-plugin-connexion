package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.intention.suppressor.RenameToSelfFilter
import com.intellij.codeInsight.daemon.impl.IntentionActionFilter
import com.intellij.openapi.application.ApplicationManager
import com.jetbrains.python.inspections.PyMethodParametersInspection
import fixtures.TestBase

class AddSelfParameterTest : TestBase() {

    fun testAddSelfParameterAvailableAndRenameSuppressed() {
        // Register filter manually via Extension Point
        val filter = RenameToSelfFilter()
        val ep = ApplicationManager.getApplication().extensionArea
            .getExtensionPoint(IntentionActionFilter.EXTENSION_POINT_NAME)
        ep.registerExtension(filter, myFixture.testRootDisposable)

        try {
            // Enable the inspection that produces "Rename to self"
            myFixture.enableInspections(PyMethodParametersInspection::class.java)

            myFixture.configureByText(
                "a.py", """
                class A:
                    def foo(bar<caret>):
                        pass
            """.trimIndent()
            )

            val intentions = myFixture.availableIntentions.map { it.text }

            assertFalse("Should not contain 'Rename to self'", intentions.contains("Rename to 'self'"))
            assertTrue("Should contain 'BetterPy: Add 'self' parameter'", intentions.contains("BetterPy: Add 'self' parameter"))

            myFixture.launchAction(myFixture.findSingleIntention("BetterPy: Add 'self' parameter"))

            myFixture.checkResult(
                """
                class A:
                    def foo(self, bar):
                        pass
            """.trimIndent()
            )
        } finally {
        }
    }

    fun testAddSelfToEmptyParameters() {
        myFixture.configureByText(
            "a.py", """
            class A:
                def foo(<caret>):
                    pass
        """.trimIndent()
        )

        val intentions = myFixture.availableIntentions.map { it.text }
        assertTrue("Should contain 'BetterPy: Add 'self' parameter'", intentions.contains("BetterPy: Add 'self' parameter"))

        myFixture.launchAction(myFixture.findSingleIntention("BetterPy: Add 'self' parameter"))

        myFixture.checkResult(
            """
            class A:
                def foo(self):
                    pass
        """.trimIndent()
        )
    }
}
