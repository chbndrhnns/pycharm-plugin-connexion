package com.github.chbndrhnns.intellijplatformplugincopy.refactoring

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import fixtures.TestBase

class PyShadowingStdlibRenameProcessorTest : TestBase() {

    fun testRenameToStdlibWarning() {
        val file = myFixture.configureByText("my_script.py", "import os")

        // Mock the dialog to return NO (Cancel)
        TestDialogManager.setTestDialog { Messages.NO }

        try {
            myFixture.renameElement(file, "os.py")
            fail("Should have thrown ProcessCanceledException")
        } catch (_: ProcessCanceledException) {
            // Success
        } catch (e: Exception) {
            val message = e.message ?: e.cause?.message ?: ""
            if (e.cause is ProcessCanceledException) {
                // Success
            } else {
                fail("Expected ProcessCanceledException but got ${e::class.simpleName}: $message")
            }
        } finally {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }
    }

    fun testRenameToStdlibAccepted() {
        val file = myFixture.configureByText("my_script.py", "import os")

        // Mock the dialog to return YES (Rename Anyway)
        TestDialogManager.setTestDialog(TestDialog.YES)

        try {
            myFixture.renameElement(file, "os.py")
            assertEquals("os.py", file.name)
        } finally {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }
    }

    fun testRenameToNonStdlib() {
        val file = myFixture.configureByText("my_script.py", "import os")

        myFixture.renameElement(file, "new_name.py")
        assertEquals("new_name.py", file.name)
    }
}
