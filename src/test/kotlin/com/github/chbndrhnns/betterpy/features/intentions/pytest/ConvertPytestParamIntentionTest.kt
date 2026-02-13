package com.github.chbndrhnns.betterpy.features.intentions.pytest

import fixtures.TestBase

class ConvertPytestParamIntentionTest : TestBase() {

    fun testPreviewConvertPlainValuesToPytestParam() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x", [1, <caret>2, 3])
            def test_example(x):
                assert x > 0
        """.trimIndent()
        )

        myFixture.doHighlighting()

        val intention = myFixture.findSingleIntention("BetterPy: Convert argument to pytest.param()")
        val previewText = myFixture.getIntentionPreviewText(intention)
        assertEquals(
            "@pytest.mark.parametrize(\"x\", [pytest.param(1), pytest.param(2), pytest.param(3)])",
            previewText
        )
    }

    fun testPreviewConvertPlainValuesToPytestParamShowsImportWhenMissing() {
        myFixture.configureByText(
            "test_example.py", """
            @pytest.mark.parametrize("x", [1, <caret>2, 3])
            def test_example(x):
                assert x > 0
        """.trimIndent()
        )

        myFixture.doHighlighting()

        val intention = myFixture.findSingleIntention("BetterPy: Convert argument to pytest.param()")
        val previewText = myFixture.getIntentionPreviewText(intention)
        assertEquals(
            "import pytest\n@pytest.mark.parametrize(\"x\", [pytest.param(1), pytest.param(2), pytest.param(3)])",
            previewText
        )
    }

    fun testPreviewConvertPytestParamToPlainValues() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x", [pytest.param(1), pytest.param(2)])
            def test_<caret>example(x):
                assert x > 0
        """.trimIndent()
        )

        myFixture.doHighlighting()

        val intention = myFixture.findSingleIntention("BetterPy: Convert pytest.param() to plain values")
        val previewText = myFixture.getIntentionPreviewText(intention)
        assertEquals(
            "@pytest.mark.parametrize(\"x\", [1, 2])",
            previewText
        )
    }

    fun testConvertPlainValuesToPytestParam() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x", [1, <caret>2, 3])
            def test_example(x):
                assert x > 0
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Convert argument to pytest.param()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("x", [pytest.param(1), pytest.param(2), pytest.param(3)])
            def test_example(x):
                assert x > 0
        """.trimIndent()
        )
    }

    fun testConvertPlainValuesToPytestParamWithStrings() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("name", ["alice", <caret>"bob", "charlie"])
            def test_names(name):
                assert len(name) > 0
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Convert argument to pytest.param()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("name", [pytest.param("alice"), pytest.param("bob"), pytest.param("charlie")])
            def test_names(name):
                assert len(name) > 0
        """.trimIndent()
        )
    }

    fun testConvertPlainTuplesToPytestParam() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x,y", [(1, 2), <caret>(3, 4), (5, 6)])
            def test_tuples(x, y):
                assert x < y
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Convert argument to pytest.param()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("x,y", [pytest.param((1, 2)), pytest.param((3, 4)), pytest.param((5, 6))])
            def test_tuples(x, y):
                assert x < y
        """.trimIndent()
        )
    }

    fun testConvertMixedValuesOnlyConvertsPlain() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x", [1, <caret>pytest.param(2), 3])
            def test_mixed(x):
                assert x > 0
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Convert argument to pytest.param()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("x", [pytest.param(1), pytest.param(2), pytest.param(3)])
            def test_mixed(x):
                assert x > 0
        """.trimIndent()
        )
    }

    fun testConvertAddsImportIfMissing() {
        myFixture.configureByText(
            "test_example.py", """
            @pytest.mark.parametrize("x", [1, <caret>2, 3])
            def test_example(x):
                assert x > 0
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Convert argument to pytest.param()")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue("Should add pytest import", result.contains("import pytest"))
        assertTrue("Should convert to pytest.param", result.contains("pytest.param(1)"))
    }

    // Tests for ConvertToPlainParametrizeValuesIntention (pytest.param() -> plain values)

    fun testConvertPytestParamToPlainValues() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x", [pytest.param(1), pytest.param(2), pytest.param(3)])
            def test_<caret>example(x):
                assert x > 0
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Convert pytest.param() to plain values")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("x", [1, 2, 3])
            def test_example(x):
                assert x > 0
        """.trimIndent()
        )
    }

    fun testConvertPytestParamToPlainStrings() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("name", [pytest.param("alice"), pytest.param("bob")])
            def test_<caret>names(name):
                assert len(name) > 0
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Convert pytest.param() to plain values")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("name", ["alice", "bob"])
            def test_names(name):
                assert len(name) > 0
        """.trimIndent()
        )
    }

    fun testConvertPytestParamToPlainTuples() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x,y", [pytest.param((1, 2)), pytest.param((3, 4))])
            def test_<caret>tuples(x, y):
                assert x < y
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Convert pytest.param() to plain values")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("x,y", [(1, 2), (3, 4)])
            def test_tuples(x, y):
                assert x < y
        """.trimIndent()
        )
    }

    fun testDoesNotConvertPytestParamWithMarks() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x", [pytest.param(1, marks=pytest.mark.skip), pytest.param(2)])
            def test_<caret>example(x):
                assert x > 0
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Convert pytest.param() to plain values")
        myFixture.launchAction(intention)

        // Only the second param without marks should be converted
        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("x", [pytest.param(1, marks=pytest.mark.skip), 2])
            def test_example(x):
                assert x > 0
        """.trimIndent()
        )
    }

    fun testDoesNotConvertPytestParamWithId() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x", [pytest.param(1, id="first"), pytest.param(2)])
            def test_<caret>example(x):
                assert x > 0
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Convert pytest.param() to plain values")
        myFixture.launchAction(intention)

        // Only the second param without id should be converted
        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("x", [pytest.param(1, id="first"), 2])
            def test_example(x):
                assert x > 0
        """.trimIndent()
        )
    }

    fun testConvertMixedPytestParamOnlyConvertsSimple() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x", [pytest.param(1), 2, pytest.param(3)])
            def test_<caret>mixed(x):
                assert x > 0
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Convert pytest.param() to plain values")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("x", [1, 2, 3])
            def test_mixed(x):
                assert x > 0
        """.trimIndent()
        )
    }

    // Availability tests

    fun testConvertToPytestParamNotAvailableWhenAllAlreadyWrapped() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x", [pytest.param(1), <caret>pytest.param(2)])
            def test_example(x):
                assert x > 0
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Convert argument to pytest.param()")
        assertTrue("Should not be available when all values are already pytest.param()", intentions.isEmpty())
    }

    fun testConvertToPlainNotAvailableWhenAllAlreadyPlain() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x", [1, 2, 3])
            def test_<caret>example(x):
                assert x > 0
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Convert pytest.param() to plain values")
        assertTrue("Should not be available when all values are already plain", intentions.isEmpty())
    }

    fun testNotAvailableOutsideParametrizeDecorator() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            def test_<caret>example():
                values = [1, 2, 3]
                assert True
        """.trimIndent()
        )

        val toPytestParam = myFixture.filterAvailableIntentions("BetterPy: Convert argument to pytest.param()")
        val toPlain = myFixture.filterAvailableIntentions("BetterPy: Convert pytest.param() to plain values")

        assertTrue("ConvertToPytestParam should not be available outside decorator", toPytestParam.isEmpty())
        assertTrue("ConvertToPlain should not be available outside decorator", toPlain.isEmpty())
    }

    fun testConvertToPytestParamNotAvailableWhenCaretOnParamNames() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("<caret>x", [1, 2, 3])
            def test_example(x):
                assert x > 0
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Convert argument to pytest.param()")
        assertTrue(
            "Should not be available when caret is on the parameter names string, not inside argvalues",
            intentions.isEmpty()
        )
    }

    fun testConvertToPytestParamNotAvailableWhenCaretInFunctionBody() {
        myFixture.configureByText(
            "test_example.py", """
            import pytest
            
            @pytest.mark.parametrize("x", [1, 2, 3])
            def test_example(x):
                assert <caret>x > 0
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Convert argument to pytest.param()")
        assertTrue(
            "Should not be available when caret is inside the decorated function (only inside the parametrize decorator)",
            intentions.isEmpty()
        )
    }
}
