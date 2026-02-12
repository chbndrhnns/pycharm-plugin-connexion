package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.jetbrains.python.psi.PyNamedParameter
import fixtures.TestBase

/**
 * Tests for pytest parametrize name string references to function parameters.
 * The first argument of @pytest.mark.parametrize("name", [...]) should be a
 * hard reference to the decorated function's parameter with that name.
 */
class PytestParametrizeNameReferenceTest : TestBase() {

    fun testParametrizeStringResolvesToFunctionParameter() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize("my_par<caret>am", [1, 2, 3])
            def test_something(my_param):
                assert my_param > 0
        """.trimIndent()
        )

        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to function parameter", resolved)
        assertInstanceOf(resolved, PyNamedParameter::class.java)
        assertEquals("my_param", (resolved as PyNamedParameter).name)
    }

    fun testParametrizeCommaDelimitedStringResolvesToFunctionParameter() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize("first<caret>, second", [(1, 2), (3, 4)])
            def test_something(first, second):
                assert first < second
        """.trimIndent()
        )

        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to function parameter", resolved)
        assertInstanceOf(resolved, PyNamedParameter::class.java)
        assertEquals("first", (resolved as PyNamedParameter).name)
    }

    fun testParametrizeCommaDelimitedStringResolvesSecondParam() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize("first, sec<caret>ond", [(1, 2), (3, 4)])
            def test_something(first, second):
                assert first < second
        """.trimIndent()
        )

        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to function parameter", resolved)
        assertInstanceOf(resolved, PyNamedParameter::class.java)
        assertEquals("second", (resolved as PyNamedParameter).name)
    }

    fun testParametrizeRenameFromStringUpdatesParameter() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize("my_par<caret>am", [1, 2, 3])
            def test_something(my_param):
                assert my_param > 0
        """.trimIndent()
        )

        val resolved = myFixture.elementAtCaret
        assertNotNull(resolved)
        myFixture.renameElementAtCaret("renamed_param")

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("renamed_param", [1, 2, 3])
            def test_something(renamed_param):
                assert renamed_param > 0
        """.trimIndent()
        )
    }

    fun testParametrizeNoReferenceForNonParametrizeDecorator() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.skip("some_na<caret>me")
            def test_something():
                pass
        """.trimIndent()
        )

        val ref = myFixture.getReferenceAtCaretPosition()
        if (ref != null) {
            val resolved = ref.resolve()
            if (resolved is PyNamedParameter) {
                fail("Should not resolve to a parameter for non-parametrize decorator")
            }
        }
    }

    fun testParametrizeStringInTupleResolvesToFunctionParameter() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize(("my_par<caret>am",), [(1,), (2,), (3,)])
            def test_something(my_param):
                assert my_param > 0
        """.trimIndent()
        )

        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to function parameter", resolved)
        assertInstanceOf(resolved, PyNamedParameter::class.java)
        assertEquals("my_param", (resolved as PyNamedParameter).name)
    }

    fun testParametrizeMultipleStringsInTupleResolve() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize(("first", "sec<caret>ond"), [(1, 2), (3, 4)])
            def test_something(first, second):
                assert first < second
        """.trimIndent()
        )

        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to function parameter", resolved)
        assertInstanceOf(resolved, PyNamedParameter::class.java)
        assertEquals("second", (resolved as PyNamedParameter).name)
    }

    fun testParametrizeStringWithArgnames() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize(argnames="my_par<caret>am", argvalues=[1, 2, 3])
            def test_something(my_param):
                assert my_param > 0
        """.trimIndent()
        )

        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to function parameter", resolved)
        assertInstanceOf(resolved, PyNamedParameter::class.java)
        assertEquals("my_param", (resolved as PyNamedParameter).name)
    }
}
