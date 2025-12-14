package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.services.DiffData
import com.github.chbndrhnns.intellijplatformplugincopy.services.TestFailureState
import fixtures.TestBase

class ReplaceExpectedWithActualIntentionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        // Clear state before each test
        TestFailureState.getInstance(myFixture.project).clearAll()
    }

    fun `test intention is available when failure exists`() {
        // ... (setup code)
        myFixture.configureByText(
            "test_module.py", """
            class TestClass:
                def test_method(self):
                    assert "exp<caret>ected" == "actual"
        """.trimIndent()
        )

        // ... (rest of test)

        // In a real env, we'd have the qname. Here we guess.
        // If the file is not in a source root, qname might be null or short.
        // TestBase usually adds src/test/testData or similar as source root?
        // fixtures.TestBase adds content root.

        // Let's try "python:test_module.TestClass.test_method" assuming test_module is the module.
        // Or "python:TestClass.test_method" if module is default.

        // To make it robust, we can set the data for BOTH potential keys.
        val project = myFixture.project
        val state = TestFailureState.getInstance(project)
        state.setDiffData("python:test_module.TestClass.test_method", DiffData("expected", "actual"))
        state.setDiffData("python:TestClass.test_method", DiffData("expected", "actual"))
        state.setDiffData("python:test_method", DiffData("expected", "actual")) // just in case

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        assertNotNull(intention)
    }

    fun `test intention invocation replaces expected with actual`() {
        myFixture.configureByText(
            "test_repro.py", """
            class TestClass:
                def test_foo(self):
                    assert "fo<caret>o" == "bar"
        """.trimIndent()
        )

        val project = myFixture.project
        val state = TestFailureState.getInstance(project)
        // Add multiple variants to be safe
        state.setDiffData("python:test_repro.TestClass.test_foo", DiffData("foo", "bar"))
        state.setDiffData("python:TestClass.test_foo", DiffData("foo", "bar"))

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class TestClass:
                def test_foo(self):
                    assert "bar" == "bar"
        """.trimIndent()
        )
    }

    fun `test numeric literal replacement`() {
        myFixture.configureByText(
            "test_num.py", """
            def test_num():
                assert 1<caret> == 2
        """.trimIndent()
        )

        val project = myFixture.project
        val state = TestFailureState.getInstance(project)
        state.setDiffData("python:test_num.test_num", DiffData("1", "2"))
        state.setDiffData("python:test_num", DiffData("1", "2"))

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def test_num():
                assert 2 == 2
        """.trimIndent()
        )
    }

    fun `test intention is available on assert keyword`() {
        myFixture.configureByText(
            "test_keyword.py", """
            def test_keyword():
                ass<caret>ert "foo" == "bar"
        """.trimIndent()
        )

        val project = myFixture.project
        val state = TestFailureState.getInstance(project)
        state.setDiffData("python:test_keyword.test_keyword", DiffData("foo", "bar"))
        state.setDiffData("python:test_keyword", DiffData("foo", "bar"))

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        assertNotNull(intention)
    }

    fun `test intention is NOT available on other statement`() {
        myFixture.configureByText(
            "test_other.py", """
            def test_other():
                x <caret>= 1
                assert "foo" == "bar"
        """.trimIndent()
        )

        val project = myFixture.project
        val state = TestFailureState.getInstance(project)
        state.setDiffData("python:test_other.test_other", DiffData("foo", "bar"))
        state.setDiffData("python:test_other", DiffData("foo", "bar"))

        val intention = myFixture.getAvailableIntention("Use actual test outcome")
        assertNull("Intention should not be available on assignment", intention)
    }

    fun `test intention only replaces inside current assert`() {
        myFixture.configureByText(
            "test_scope.py", """
            def test_scope():
                assert "foo" == "baz"
                assert "fo<caret>o" == "bar"
        """.trimIndent()
        )

        val project = myFixture.project
        val state = TestFailureState.getInstance(project)
        state.setDiffData("python:test_scope.test_scope", DiffData("foo", "bar"))
        state.setDiffData("python:test_scope", DiffData("foo", "bar"))

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def test_scope():
                assert "foo" == "baz"
                assert "bar" == "bar"
        """.trimIndent()
        )
    }
}
