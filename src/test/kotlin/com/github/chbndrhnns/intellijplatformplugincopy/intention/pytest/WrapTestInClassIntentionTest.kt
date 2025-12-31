package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class WrapTestInClassIntentionTest : TestBase() {

    // Basic wrapping scenarios
    fun testWrapSimpleTestFunction() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    fun testWrapTestFunctionWithSingleParameter() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    fun testWrapTestFunctionWithMultipleParameters() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    fun testWrapTestFunctionWithDocstring() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    fun testWrapTestFunctionWithMultipleStatements() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    // Pytest features - decorators
    fun testWrapTestWithParametrizeDecorator() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    fun testWrapTestWithSkipDecorator() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    fun testWrapTestWithMultipleDecorators() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    // Availability tests
    fun testNotAvailableOnNonTestFunction() {
        myFixture.assertIntentionNotAvailable(
            "test_foo.py",
            """
            def helper_function():
                <caret>pass
            """,
            "BetterPy: Wrap test in class"
        )
    }

    fun testNotAvailableOnTestMethodInsideClass() {
        myFixture.assertIntentionNotAvailable(
            "test_foo.py",
            """
            class TestSomething:
                def test_method(self):
                    <caret>pass
            """,
            "BetterPy: Wrap test in class"
        )
    }

    fun testNotAvailableOnNonPythonFile() {
        myFixture.configureByText("test.txt", "test_something<caret>")
        val intention = myFixture.availableIntentions.find { it.text == "BetterPy: Wrap test in class" }
        assertNull("Intention should not be available in non-Python files", intention)
    }

    fun testAvailableOnFunctionName() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    fun testAvailableOnDefKeyword() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    // Edge cases
    fun testWrapTestWithTypeHints() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    fun testWrapTestWithDefaultParameters() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    fun testWrapTestWithComplexName() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    fun testWrapTestWithSingleWordName() {
        myFixture.doIntentionTest(
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
            "BetterPy: Wrap test in class"
        )
    }

    // Settings toggle test
    fun testNotAvailableWhenSettingDisabled() {
        val settings = com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState.instance()
        val originalValue = settings.state.enableWrapTestInClassIntention
        try {
            settings.state.enableWrapTestInClassIntention = false
            myFixture.assertIntentionNotAvailable(
                "test_foo.py",
                """
                def test_something():
                    <caret>pass
                """,
                "BetterPy: Wrap test in class"
            )
        } finally {
            settings.state.enableWrapTestInClassIntention = originalValue
        }
    }
}
