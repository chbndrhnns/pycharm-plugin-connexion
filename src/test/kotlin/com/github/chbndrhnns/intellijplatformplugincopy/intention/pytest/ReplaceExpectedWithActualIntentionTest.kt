package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.services.DiffData
import com.github.chbndrhnns.intellijplatformplugincopy.services.TestFailureState
import com.intellij.openapi.roots.ProjectRootManager
import fixtures.TestBase

class ReplaceExpectedWithActualIntentionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        // Clear state before each test
        TestFailureState.getInstance(myFixture.project).clearAll()
    }

    private fun setDiffData(qName: String, expected: String, actual: String) {
        val file = myFixture.file
        val root = ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(file.virtualFile) 
                   ?: ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file.virtualFile)
        val path = root?.path ?: ""
        val key = "python<$path>://$qName"
        TestFailureState.getInstance(project).setDiffData(key, DiffData(expected, actual))
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
        setDiffData("test_module.TestClass.test_method", "expected", "actual")

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
        TestFailureState.getInstance(project)
        // Add multiple variants to be safe
        setDiffData("test_repro.TestClass.test_foo", "foo", "bar")

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

        setDiffData("test_num.test_num", "1", "2")

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

        setDiffData("test_keyword.test_keyword", "foo", "bar")

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

        setDiffData("test_other.test_other", "foo", "bar")

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

        setDiffData("test_scope.test_scope", "foo", "bar")

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
