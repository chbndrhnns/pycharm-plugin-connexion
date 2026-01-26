package com.github.chbndrhnns.betterpy.features.pytest.fixture

import fixtures.TestBase

class PytestFixtureOverrideGuardTest : TestBase() {

    fun testNotAvailableInsideFunction() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            @pytest.fixture
            def my_fixture(): return 1
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_guard.py", """
            def test_func():
                <caret>
                pass
        """.trimIndent()
        )

        val handler = PytestFixtureOverrideMethodsHandler()
        assertFalse(
            "Should not be valid caret inside function",
            handler.isValidCaretPosition(myFixture.editor, myFixture.file as com.jetbrains.python.psi.PyFile)
        )
        assertFalse(
            "Should not be valid inside function (integration)",
            handler.isValidFor(myFixture.editor, myFixture.file)
        )
    }

    fun testNotAvailableInsideMethod() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            @pytest.fixture
            def my_fixture(): return 1
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_guard_class.py", """
            class TestClass:
                def test_method(self):
                    <caret>
                    pass
        """.trimIndent()
        )

        val handler = PytestFixtureOverrideMethodsHandler()
        assertFalse(
            "Should not be valid caret inside method",
            handler.isValidCaretPosition(myFixture.editor, myFixture.file as com.jetbrains.python.psi.PyFile)
        )
    }

    fun testNotAvailableOnNonEmptyLine() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            @pytest.fixture
            def my_fixture(): return 1
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_guard_line.py", """
            x = 1 <caret>
        """.trimIndent()
        )

        val handler = PytestFixtureOverrideMethodsHandler()
        // Note: delegate.isValidFor might return true for general override if context allows, 
        // but for our purpose we want to ensure *our* logic sees it as invalid for fixture override.
        // However, isValidFor returns (candidates || delegate). 
        // If we are on x=1, there are no candidates for fixture override if we implement the guard correctly.
        // And usually PyOverrideMethodsHandler is for classes. At module level it might be false.

        // Let's check candidates logic directly via reflection or similar if needed, 
        // but here we check the handler's public API. 
        // If the delegate returns false (which it likely does at module level on assignment), 
        // then we expect false.
        assertFalse(
            "Should not be valid caret on non-empty line",
            handler.isValidCaretPosition(myFixture.editor, myFixture.file as com.jetbrains.python.psi.PyFile)
        )
    }

    fun testAvailableAtModuleLevelEmptyLine() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            @pytest.fixture
            def my_fixture(): return 1
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_guard_valid.py", """
            import pytest
            <caret>
        """.trimIndent()
        )

        val handler = PytestFixtureOverrideMethodsHandler()
        assertTrue(
            "Should be valid caret at module level empty line",
            handler.isValidCaretPosition(myFixture.editor, myFixture.file as com.jetbrains.python.psi.PyFile)
        )
        assertTrue(
            "Should be valid at module level empty line (integration)",
            handler.isValidFor(myFixture.editor, myFixture.file)
        )
    }

    fun testAvailableInClassEmptyLine() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            @pytest.fixture
            def my_fixture(): return 1
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_guard_class_valid.py", """
            class TestClass:
                <caret>
                pass
        """.trimIndent()
        )

        val handler = PytestFixtureOverrideMethodsHandler()
        assertTrue(
            "Should be valid caret in class empty line",
            handler.isValidCaretPosition(myFixture.editor, myFixture.file as com.jetbrains.python.psi.PyFile)
        )
        assertTrue(
            "Should be valid in class empty line (integration)",
            handler.isValidFor(myFixture.editor, myFixture.file)
        )
    }
}
