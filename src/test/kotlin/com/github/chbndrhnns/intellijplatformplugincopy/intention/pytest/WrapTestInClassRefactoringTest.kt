package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import fixtures.TestBase
import fixtures.doRefactoringActionTest
import fixtures.updateRefactoringAction

class WrapTestInClassRefactoringTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest.WrapTestInClassRefactoringAction"

    // Basic wrapping scenarios
    fun testWrapSimpleTestFunction() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_user_login():
                <caret>assert True
            """,
            """
            class TestUserLogin:
                def test_user_login(self):
                    assert True
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testWrapTestFunctionWithSingleParameter() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_database_connection(db_session):
                <caret>assert db_session.is_connected()
            """,
            """
            class TestDatabaseConnection:
                def test_database_connection(self, db_session):
                    assert db_session.is_connected()
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testWrapTestFunctionWithMultipleParameters() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_add(a, b, expected):
                <caret>assert a + b == expected
            """,
            """
            class TestAdd:
                def test_add(self, a, b, expected):
                    assert a + b == expected
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testWrapTestFunctionWithDocstring() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_something():
                '''This is a test docstring.'''
                <caret>assert True
            """,
            """
            class TestSomething:
                def test_something(self):
                    '''This is a test docstring.'''
                    assert True
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testWrapTestFunctionWithMultipleStatements() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_complex():
                <caret>x = 1
                y = 2
                assert x + y == 3
            """,
            """
            class TestComplex:
                def test_complex(self):
                    x = 1
                    y = 2
                    assert x + y == 3
            """,
            actionId,
            dialogOk = true
        )
    }

    // Pytest features - decorators
    fun testWrapTestWithParametrizeDecorator() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            import pytest
            
            @pytest.mark.parametrize("input,expected", [(1, 2), (2, 3)])
            def test_increment(input, expected):
                <caret>assert input + 1 == expected
            """,
            """
            import pytest
            
            
            class TestIncrement:
                @pytest.mark.parametrize("input,expected", [(1, 2), (2, 3)])
                def test_increment(self, input, expected):
                    assert input + 1 == expected
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testWrapTestWithSkipDecorator() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            import pytest
                        
            @pytest.mark.skip(reason="Not implemented yet")
            def test_future_feature():
                <caret>pass
            """,
            """
            import pytest
            

            class TestFutureFeature:
                @pytest.mark.skip(reason="Not implemented yet")
                def test_future_feature(self):
                    pass
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testWrapTestWithMultipleDecorators() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            import pytest
            
            
            @pytest.mark.slow
            @pytest.mark.parametrize("value", [1, 2, 3])
            def test_slow_operation(value):
                <caret>assert value > 0
            """,
            """
            import pytest

            
            class TestSlowOperation:
                @pytest.mark.slow
                @pytest.mark.parametrize("value", [1, 2, 3])
                def test_slow_operation(self, value):
                    assert value > 0
            """,
            actionId,
            dialogOk = true
        )
    }

    // Availability tests
    fun testNotAvailableOnNonTestFunction() {
        myFixture.configureByText(
            "test_foo.py",
            """
            def helper_function():
                <caret>pass
            """
        )
        val event = myFixture.updateRefactoringAction(actionId)
        assertFalse("Action should not be enabled on non-test functions", event.presentation.isEnabled)
    }

    fun testNotAvailableOnTestMethodInsideClass() {
        myFixture.configureByText(
            "test_foo.py",
            """
            class TestSomething:
                def test_method(self):
                    <caret>pass
            """
        )
        val event = myFixture.updateRefactoringAction(actionId)
        assertFalse("Action should not be enabled on test methods already inside a class", event.presentation.isEnabled)
    }

    fun testNotAvailableOnNonPythonFile() {
        myFixture.configureByText("test.txt", "test_something<caret>")
        val event = myFixture.updateRefactoringAction(actionId)
        assertFalse("Action should not be enabled in non-Python files", event.presentation.isEnabled)
    }

    fun testAvailableOnFunctionName() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_<caret>something():
                assert True
            """,
            """
            class TestSomething:
                def test_something(self):
                    assert True
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testAvailableOnDefKeyword() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            <caret>def test_something():
                assert True
            """,
            """
            class TestSomething:
                def test_something(self):
                    assert True
            """,
            actionId,
            dialogOk = true
        )
    }

    // Edge cases
    fun testWrapTestWithTypeHints() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_typed(value: int) -> None:
                <caret>assert value > 0
            """,
            """
            class TestTyped:
                def test_typed(self, value: int) -> None:
                    assert value > 0
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testWrapTestWithDefaultParameters() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_with_default(value=10):
                <caret>assert value == 10
            """,
            """
            class TestWithDefault:
                def test_with_default(self, value=10):
                    assert value == 10
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testWrapTestWithComplexName() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_user_can_login_with_valid_credentials():
                <caret>assert True
            """,
            """
            class TestUserCanLoginWithValidCredentials:
                def test_user_can_login_with_valid_credentials(self):
                    assert True
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testWrapTestWithSingleWordName() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_simple():
                <caret>assert True
            """,
            """
            class TestSimple:
                def test_simple(self):
                    assert True
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testWrapFunctionWithEllipsis() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_b():
                <caret>...
            """,
            """
            class TestB:
                def test_b(self):
                    ...
            """,
            actionId,
            dialogOk = true
        )
    }

    fun testWrapFunctionWithPass() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            def test_b():
                <caret>pass
            """,
            """
            class TestB:
                def test_b(self):
                    pass
            """,
            actionId,
            dialogOk = true
        )
    }

    // Test for adding to existing class (regression test for nested function bug)
    fun testAddToExistingClassDoesNotCreateNestedFunction() {
        // This test verifies the fix for the bug where adding a function to an existing class
        // would incorrectly nest it inside another method instead of adding it as a sibling
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            class TestA:
                def test_a(self):
                    ...
            
            
            def test_b():
                <caret>...
            """,
            """
            class TestA:
                def test_a(self):
                    ...
            
            
            class TestB:
                def test_b(self):
                    ...
            """,
            actionId,
            dialogOk = true
        )

        // Verify no nested functions were created
        val result = myFixture.file.text
        assertFalse(
            "Should not contain nested 'def test_a'",
            result.contains("def test_b(self):\n\n        def test_a")
        )
        assertFalse("Should not contain nested 'def test_'", result.contains("def test_b(self):\n\n        def test_"))
    }

    // Settings toggle test
    fun testNotAvailableWhenSettingDisabled() {
        val settings = com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState.instance()
        val originalValue = settings.state.enableWrapTestInClassIntention
        try {
            settings.state.enableWrapTestInClassIntention = false
            myFixture.configureByText(
                "test_foo.py",
                """
                def test_something():
                    <caret>pass
                """
            )
            val event = myFixture.updateRefactoringAction(actionId)
            assertFalse("Action should not be enabled when setting is disabled", event.presentation.isEnabled)
        } finally {
            settings.state.enableWrapTestInClassIntention = originalValue
        }
    }
}
