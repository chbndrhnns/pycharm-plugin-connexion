package com.github.chbndrhnns.betterpy.features.pytest.surround

import com.intellij.openapi.command.WriteCommandAction
import fixtures.TestBase

class PytestRaisesSurrounderTest : TestBase() {

    fun testWrapsSelectedStatementInTestFunction() {
        myFixture.configureByText(
            "test_example.py",
            """
            def test_something():
                <selection>raise ValueError()</selection>
            """.trimIndent()
        )

        surroundSelection()

        assertResult(
            """
            import pytest


            def test_something():
                with pytest.raises(Exception):
                    raise ValueError()
            """.trimIndent()
        )
    }

    fun testNotAvailableOutsideTestFunction() {
        myFixture.configureByText(
            "example.py",
            """
            def helper():
                <selection>raise ValueError()</selection>
            """.trimIndent()
        )

        assertFalse(isSelectionApplicable())
    }

    fun testNotAvailableInsideExistingPytestRaises() {
        myFixture.configureByText(
            "test_example.py",
            """
            import pytest

            def test_something():
                with pytest.raises(Exception):
                    <selection>raise ValueError()</selection>
            """.trimIndent()
        )

        assertFalse(isSelectionApplicable())
    }

    fun testDoesNotSplitLinesWhenSurrounding() {
        myFixture.configureByText(
            "test_example.py",
            """
            def test_something():
                <selection>raise ValueError()</selection>
            """.trimIndent()
        )

        surroundSelection()

        assertResult(
            """
            import pytest


            def test_something():
                with pytest.raises(Exception):
                    raise ValueError()
            """.trimIndent()
        )
    }

    private fun surroundSelection() {
        val descriptor = PytestSurroundDescriptor()
        val selection = myFixture.editor.selectionModel
        val elements = descriptor.getElementsToSurround(
            myFixture.file,
            selection.selectionStart,
            selection.selectionEnd
        )
        val surrounder = descriptor.surrounders.single { it.templateDescription == "pytest.raises()" }
        assertTrue(surrounder.isApplicable(elements))
        WriteCommandAction.runWriteCommandAction(project) {
            surrounder.surroundElements(project, myFixture.editor, elements)
        }
    }

    private fun isSelectionApplicable(): Boolean {
        val descriptor = PytestSurroundDescriptor()
        val selection = myFixture.editor.selectionModel
        val elements = descriptor.getElementsToSurround(
            myFixture.file,
            selection.selectionStart,
            selection.selectionEnd
        )
        val surrounder = descriptor.surrounders.single { it.templateDescription == "pytest.raises()" }
        return surrounder.isApplicable(elements)
    }

    private fun assertResult(expected: String) {
        val actual = myFixture.file.text
        var normalizedExpected = expected
        if (!normalizedExpected.endsWith("\n") && actual.endsWith("\n")) {
            normalizedExpected += "\n"
        }
        assertEquals(normalizedExpected, actual)
    }
}
