package com.github.chbndrhnns.intellijplatformplugincopy.intention.exceptions

import fixtures.TestBase
import fixtures.doIntentionTest

class AddExceptionCaptureIntentionTest : TestBase() {

    fun testAddExceptionCapture_Generic() {
        myFixture.doIntentionTest(
            "test_file.py",
            """
            def test_():
                try:
                    ...
                except Exception:
                    raise Excepti<caret>on("Test failed") from original_error
            """,
            """
            def test_():
                try:
                    ...
                except Exception as original_error:
                    raise Exception("Test failed") from original_error
            """,
            "Add 'as original_error' to except clause"
        )
    }
}
