package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import fixtures.TestBase

class ReplaceExpectedWithActualIntentionParametrizeTest : TestBase() {

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

    private fun buildKey(qName: String): String {
        val projectBasePath = project.basePath ?: return qName
        return "python<$projectBasePath>://$qName"
    }

    fun `test intention supports parametrized test failure`() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize("arg, expected", [("abc", "defg")], ids=["case-1"])
            def test_str(arg, expected):
                assert arg <caret>== expected
        """.trimIndent()
        )

        // Simulate failure for specific parameter set (identified by `ids=[...]`).
        setDiffData("test_param.test_str[case-1]", "defg", "abc")

        // Currently this should fail because the intention looks for exact match on "test_param.test_str"
        val intention = myFixture.getAvailableIntention("BetterPy: Use actual test outcome")
        assertNotNull("Intention should be available for parametrized test failure", intention)

        myFixture.launchAction(intention!!)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("arg, expected", [("abc", "abc")], ids=["case-1"])
            def test_str(arg, expected):
                assert arg == expected
        """.trimIndent()
        )
    }

    fun `test intention supports parametrized test with multiple parameter sets`() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize(
                "arg,expected",
                [("abc", "defg"), ("defg", "defg"), ],
                ids=["case-1", "case-2"],
            )
            def test_str(arg, expected):
                assert arg <caret>== expected
        """.trimIndent()
        )

        // Simulate failure for the first parameter set
        setDiffData("test_param.test_str[case-1]", "defg", "abc")

        val intention = myFixture.getAvailableIntention("BetterPy: Use actual test outcome")
        assertNotNull("Intention should be available for parametrized test failure", intention)

        myFixture.launchAction(intention!!)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize(
                "arg,expected",
                [("abc", "abc"), ("defg", "defg"), ],
                ids=["case-1", "case-2"],
            )
            def test_str(arg, expected):
                assert arg == expected
        """.trimIndent()
        )
    }

    fun `test intention prefers explicit test key when multiple parametrized diffs exist`() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest

            @pytest.mark.parametrize(
                "arg,expected",
                [("abc", "defg"), ("xxx", "yyy"), ],
                ids=["case-1", "case-2"],
            )
            def test_str(arg, expected):
                assert arg <caret>== expected
        """.trimIndent()
        )

        setDiffData("test_param.test_str[case-1]", "defg", "abc")
        setDiffData("test_param.test_str[case-2]", "yyy", "xxx")

        val intention = ReplaceExpectedWithActualIntention()
        val explicitKey = buildKey("test_param.test_str[case-2]")

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            intention.invokeWithTestKey(project, myFixture.editor, myFixture.file, explicitKey)
        }

        myFixture.checkResult(
            """
            import pytest

            @pytest.mark.parametrize(
                "arg,expected",
                [("abc", "defg"), ("xxx", "xxx"), ],
                ids=["case-1", "case-2"],
            )
            def test_str(arg, expected):
                assert arg == expected
            """.trimIndent()
        )
    }

    fun `test intention matches parametrized case by ids`() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest

            @pytest.mark.parametrize(
                "arg,expected",
                [("abc", "defg"), ("xxx", "yyy"),],
                ids=["case-1", "case-2"],
            )
            def test_str(arg, expected):
                assert arg <caret>== expected
            """.trimIndent()
        )

        setDiffData("test_param.test_str[case-2]", "yyy", "xxx")

        val intention = myFixture.getAvailableIntention("BetterPy: Use actual test outcome")
        assertNotNull("Intention should be available for parametrized test failure", intention)

        myFixture.launchAction(intention!!)

        myFixture.checkResult(
            """
            import pytest

            @pytest.mark.parametrize(
                "arg,expected",
                [("abc", "defg"), ("xxx", "xxx"), ],
                ids=["case-1", "case-2"],
            )
            def test_str(arg, expected):
                assert arg == expected
            """.trimIndent()
        )
    }

    fun `test parametrized replacement is skipped when ids are missing`() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest

            @pytest.mark.parametrize(
                "arg,expected",
                [("abc", "defg"), ("xxx", "yyy"),],
            )
            def test_str(arg, expected):
                assert arg <caret>== expected
            """.trimIndent()
        )

        // Even though the matched key contains a bracket id, we can't map it to a parameter set
        // without an explicit literal `ids=[...]`.
        setDiffData("test_param.test_str[case-2]", "yyy", "xxx")

        val intention = myFixture.getAvailableIntention("BetterPy: Use actual test outcome")
        assertNotNull("Intention should be available for parametrized test failure", intention)

        myFixture.launchAction(intention!!)

        myFixture.checkResult(
            """
            import pytest

            @pytest.mark.parametrize(
                "arg,expected",
                [("abc", "defg"), ("xxx", "yyy"),],
            )
            def test_str(arg, expected):
                assert arg == expected
            """.trimIndent()
        )
    }

    fun `test parametrized replacement is skipped when ids are not literal`() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest

            def my_ids(value):
                return str(value)

            @pytest.mark.parametrize(
                "arg,expected",
                [("abc", "defg"), ("xxx", "yyy"),],
                ids=my_ids,
            )
            def test_str(arg, expected):
                assert arg <caret>== expected
            """.trimIndent()
        )

        setDiffData("test_param.test_str[case-2]", "yyy", "xxx")

        val intention = myFixture.getAvailableIntention("BetterPy: Use actual test outcome")
        assertNotNull("Intention should be available for parametrized test failure", intention)

        myFixture.launchAction(intention!!)

        myFixture.checkResult(
            """
            import pytest

            def my_ids(value):
                return str(value)

            @pytest.mark.parametrize(
                "arg,expected",
                [("abc", "defg"), ("xxx", "yyy"),],
                ids=my_ids,
            )
            def test_str(arg, expected):
                assert arg == expected
            """.trimIndent()
        )
    }
}
