package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import fixtures.TestBase

class ReplaceExpectedWithActualIntentionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        TestOutcomeDiffService.getInstance(myFixture.project).clearAll()
    }

    private fun setDiffData(qName: String, expected: String, actual: String) {
        // SMTestProxy.locationUrl uses the project base path in angle brackets
        // The qualified name already includes the full module path
        val projectBasePath = project.basePath ?: return
        val key = "python<$projectBasePath>://$qName"
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

        setDiffData("test_module.TestClass.test_method", "actual", "expected")

        val intention = myFixture.findSingleIntention("BetterPy: Use actual test outcome")
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

        setDiffData("test_repro.TestClass.test_foo", "bar", "foo")

        val intention = myFixture.findSingleIntention("BetterPy: Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class TestClass:
                def test_foo(self):
                    assert "foo" == "foo"
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

        setDiffData("test_num.test_num", "2", "1")

        val intention = myFixture.findSingleIntention("BetterPy: Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def test_num():
                assert 1 == 1
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

        setDiffData("test_keyword.test_keyword", "bar", "foo")

        val intention = myFixture.findSingleIntention("BetterPy: Use actual test outcome")
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

        setDiffData("test_other.test_other", "bar", "foo")

        val intention = myFixture.getAvailableIntention("BetterPy: Use actual test outcome")
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

        setDiffData("test_scope.test_scope", "bar", "foo")

        val intention = myFixture.findSingleIntention("BetterPy: Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def test_scope():
                assert "foo" == "baz"
                assert "foo" == "foo"
            """.trimIndent(),
        )
    }

    fun `test dict literal replacement`() {
        myFixture.configureByText(
            "test_dict.py",
            """
            def test_dict():
                ass<caret>ert {
                    "abc": [
                        1,
                        2,
                        3,
                        4,
                        5,
                    ]
                } == {
                    "abc": [
                        1,
                        2,
                        3,
                        4,
                        5,
                        6,
                    ]
                }
            """.trimIndent(),
        )

        setDiffData(
            "test_dict.test_dict",
            "{\"abc\": [1, 2, 3, 4, 5, 6]}",
            "{\"abc\": [1, 2, 3, 4, 5]}",
        )

        val intention = myFixture.findSingleIntention("BetterPy: Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def test_dict():
                assert {
                    "abc": [
                        1,
                        2,
                        3,
                        4,
                        5,
                    ]
                } == {"abc": [1, 2, 3, 4, 5]}
            """.trimIndent(),
        )
    }
}
