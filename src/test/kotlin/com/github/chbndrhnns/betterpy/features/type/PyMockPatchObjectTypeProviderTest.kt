package com.github.chbndrhnns.betterpy.features.type

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import fixtures.TestBase

class PyMockPatchObjectTypeProviderTest : TestBase() {

    fun testPatchObjectMakesAttributeMockType() {
        myFixture.configureByText(
            "test_patch_object.py", """
            from unittest.mock import MagicMock


            class C:
                def do(self):
                    raise RuntimeError()


            def test_plain(mocker):
                c = C()
                mocker.patch.object(c, "do")
                c.do()
                c.do.assert_called_once()
        """.trimIndent()
        )

        val file = myFixture.file
        val context = TypeEvalContext.codeAnalysis(project, file)

        // Find c.do.assert_called_once reference
        val assertRef = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
            .find { it.text == "c.do.assert_called_once" }!!

        val resolveResults = assertRef.reference.multiResolve(false)
        assertTrue("assert_called_once should resolve on patched attribute", resolveResults.isNotEmpty())
    }

    fun testPatchObjectAttributeIsMockType() {
        myFixture.configureByText(
            "test_patch_object_type.py", """
            from unittest.mock import MagicMock


            class C:
                def do(self):
                    raise RuntimeError()


            def test_plain(mocker):
                c = C()
                mocker.patch.object(c, "do")
                x = c.do
        """.trimIndent()
        )

        val file = myFixture.file
        val context = TypeEvalContext.codeAnalysis(project, file)

        val expr = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
            .find { it.text == "c.do" }!!

        val type = context.getType(expr)
        assertNotNull("c.do should have a type after patch.object", type)
        assertTrue("c.do should be PyMockType after patch.object, got: ${type?.javaClass?.name}", type is PyMockType)
    }

    fun testPatchObjectWithUnittestPatch() {
        myFixture.configureByText(
            "test_unittest_patch.py", """
            from unittest.mock import patch


            class C:
                def do(self):
                    raise RuntimeError()


            def test_plain():
                c = C()
                with patch.object(c, "do"):
                    c.do.assert_called_once()
        """.trimIndent()
        )

        val file = myFixture.file

        val assertRef = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
            .find { it.text == "c.do.assert_called_once" }!!

        val resolveResults = assertRef.reference.multiResolve(false)
        assertTrue("assert_called_once should resolve on patched attribute (with statement)", resolveResults.isNotEmpty())
    }
}
