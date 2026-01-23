package com.github.chbndrhnns.betterpy.features.intentions.exceptions

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
            "BetterPy: Add 'as original_error' to except clause"
        )
    }

    fun testAddExceptionCapture_Tuple() {
        myFixture.doIntentionTest(
            "test_file_tuple.py",
            """
            def test_():
                try:
                    ...
                except (IndexError, KeyError):
                    raise Exception from e<caret>rr
            """,
            """
            def test_():
                try:
                    ...
                except (IndexError, KeyError) as err:
                    raise Exception from err
            """,
            "BetterPy: Add 'as err' to except clause"
        )
    }
}
