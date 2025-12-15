package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.openapi.roots.ProjectRootManager
import fixtures.TestBase

class ReplaceExpectedWithActualIntentionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        TestOutcomeDiffService.getInstance(myFixture.project).clearAll()
    }

    private fun setDiffData(qName: String, expected: String, actual: String) {
        val file = myFixture.file
        val root = ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(file.virtualFile)
            ?: ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file.virtualFile)
        val path = root?.path ?: ""
        val key = "python<$path>://$qName"
        TestOutcomeDiffService.getInstance(project).put(key, OutcomeDiff(expected, actual))
    }

    fun `test intention is available when failure exists`() {
        myFixture.configureByText(
            "test_module.py",
            """
            class TestClass:
                def test_method(self):
                    assert "exp<caret>ected" == "actual"
            """.trimIndent(),
        )

        setDiffData("test_module.TestClass.test_method", "expected", "actual")

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        assertNotNull(intention)
    }

    fun `test intention invocation replaces expected with actual`() {
        myFixture.configureByText(
            "test_repro.py",
            """
            class TestClass:
                def test_foo(self):
                    assert "fo<caret>o" == "bar"
            """.trimIndent(),
        )

        setDiffData("test_repro.TestClass.test_foo", "foo", "bar")

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class TestClass:
                def test_foo(self):
                    assert "bar" == "bar"
            """.trimIndent(),
        )
    }

    fun `test numeric literal replacement`() {
        myFixture.configureByText(
            "test_num.py",
            """
            def test_num():
                assert 1<caret> == 2
            """.trimIndent(),
        )

        setDiffData("test_num.test_num", "1", "2")

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def test_num():
                assert 2 == 2
            """.trimIndent(),
        )
    }

    fun `test intention is available on assert keyword`() {
        myFixture.configureByText(
            "test_keyword.py",
            """
            def test_keyword():
                ass<caret>ert "foo" == "bar"
            """.trimIndent(),
        )

        setDiffData("test_keyword.test_keyword", "foo", "bar")

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        assertNotNull(intention)
    }

    fun `test intention is NOT available on other statement`() {
        myFixture.configureByText(
            "test_other.py",
            """
            def test_other():
                x <caret>= 1
                assert "foo" == "bar"
            """.trimIndent(),
        )

        setDiffData("test_other.test_other", "foo", "bar")

        val intention = myFixture.getAvailableIntention("Use actual test outcome")
        assertNull("Intention should not be available on assignment", intention)
    }

    fun `test intention only replaces inside current assert`() {
        myFixture.configureByText(
            "test_scope.py",
            """
            def test_scope():
                assert "foo" == "baz"
                assert "fo<caret>o" == "bar"
            """.trimIndent(),
        )

        setDiffData("test_scope.test_scope", "foo", "bar")

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def test_scope():
                assert "foo" == "baz"
                assert "bar" == "bar"
            """.trimIndent(),
        )
    }
}
