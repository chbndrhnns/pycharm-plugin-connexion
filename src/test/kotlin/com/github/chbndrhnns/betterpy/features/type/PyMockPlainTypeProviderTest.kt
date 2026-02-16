package com.github.chbndrhnns.betterpy.features.type

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import fixtures.TestBase

class PyMockPlainTypeProviderTest : TestBase() {

    fun testPlainMockHasMockType() {
        myFixture.configureByText(
            "test_plain_mock.py", """
            from unittest.mock import Mock

            def test_plain():
                m = Mock()
                x = m  # <caret>
        """.trimIndent()
        )

        val file = myFixture.file
        val context = TypeEvalContext.codeAnalysis(project, file)

        val xTarget = PsiTreeUtil.findChildrenOfType(file, com.jetbrains.python.psi.PyTargetExpression::class.java)
            .find { it.text == "x" }!!
        val xValue = xTarget.findAssignedValue()!!
        val type = context.getType(xValue)

        assertNotNull("Plain Mock() should have a type", type)
        assertTrue("Plain Mock() should be PyMockType, got: ${type?.javaClass?.name}", type is PyMockType)
    }

    fun testPlainMockResolvesAssertCalledOnce() {
        myFixture.configureByText(
            "test_plain_mock_resolve.py", """
            from unittest.mock import Mock

            def test_plain():
                m = Mock()
                m.assert_called_once()
        """.trimIndent()
        )

        val file = myFixture.file
        val expr = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
            .find { it.text == "m.assert_called_once" }!!

        val resolveResults = expr.reference.multiResolve(false)
        assertTrue("assert_called_once should resolve on plain Mock", resolveResults.isNotEmpty())
    }

    fun testPlainMagicMockResolvesAssertCalledOnceWith() {
        myFixture.configureByText(
            "test_plain_magicmock.py", """
            from unittest.mock import MagicMock

            def test_plain():
                m = MagicMock()
                m.assert_called_once_with(1, 2)
        """.trimIndent()
        )

        val file = myFixture.file
        val expr = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
            .find { it.text == "m.assert_called_once_with" }!!

        val resolveResults = expr.reference.multiResolve(false)
        assertTrue("assert_called_once_with should resolve on plain MagicMock", resolveResults.isNotEmpty())
    }

    fun testPlainMockResolvesCallCount() {
        myFixture.configureByText(
            "test_plain_mock_call_count.py", """
            from unittest.mock import Mock

            def test_plain():
                m = Mock()
                print(m.call_count)
        """.trimIndent()
        )

        val file = myFixture.file
        val expr = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
            .find { it.text == "m.call_count" }!!

        val resolveResults = expr.reference.multiResolve(false)
        assertTrue("call_count should resolve on plain Mock", resolveResults.isNotEmpty())
    }

    fun testPlainMockResolvesReturnValue() {
        myFixture.configureByText(
            "test_plain_mock_return_value.py", """
            from unittest.mock import Mock

            def test_plain():
                m = Mock()
                x = m.return_value
        """.trimIndent()
        )

        val file = myFixture.file
        val expr = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
            .find { it.text == "m.return_value" }!!

        val resolveResults = expr.reference.multiResolve(false)
        assertTrue("return_value should resolve on plain Mock", resolveResults.isNotEmpty())
    }

    fun testPlainAsyncMockHasMockType() {
        myFixture.configureByText(
            "test_plain_async_mock.py", """
            from unittest.mock import AsyncMock

            def test_plain():
                m = AsyncMock()
                x = m
        """.trimIndent()
        )

        val file = myFixture.file
        val context = TypeEvalContext.codeAnalysis(project, file)

        val xTarget = PsiTreeUtil.findChildrenOfType(file, com.jetbrains.python.psi.PyTargetExpression::class.java)
            .find { it.text == "x" }!!
        val xValue = xTarget.findAssignedValue()!!
        val type = context.getType(xValue)

        assertNotNull("Plain AsyncMock() should have a type", type)
        assertTrue("Plain AsyncMock() should be PyMockType", type is PyMockType)
    }
}
