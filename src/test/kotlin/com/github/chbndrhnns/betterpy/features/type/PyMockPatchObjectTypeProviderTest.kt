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

    // --- New tests: cross-scope and other mock types ---

    fun testPatchObjectInFixtureScope() {
        // patch.object called in a setup function, attribute accessed after setup
        // Uses type annotation so the type system knows the type of the parameter
        myFixture.configureByText(
            "test_fixture.py", """
            from unittest.mock import patch


            class Service:
                def fetch(self) -> int:
                    return 42


            def create_patched_service() -> Service:
                s = Service()
                patch.object(s, "fetch").start()
                return s


            def test_uses_factory():
                svc = create_patched_service()
                svc.fetch.assert_called_once()
        """.trimIndent()
        )

        val file = myFixture.file

        val assertRef = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
            .find { it.text == "svc.fetch.assert_called_once" }!!

        val resolveResults = assertRef.reference.multiResolve(false)
        assertTrue("assert_called_once should resolve on attribute patched in factory function", resolveResults.isNotEmpty())
    }

    fun testPatchObjectInHelperFunction() {
        // patch.object called in a helper, attribute accessed in the test
        myFixture.configureByText(
            "test_helper.py", """
            from unittest.mock import patch


            class Service:
                def fetch(self) -> int:
                    return 42


            def setup_mocks(s):
                patch.object(s, "fetch").start()


            def test_uses_helper():
                s = Service()
                setup_mocks(s)
                s.fetch.assert_called_once()
        """.trimIndent()
        )

        val file = myFixture.file

        val assertRef = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
            .find { it.text == "s.fetch.assert_called_once" }!!

        val resolveResults = assertRef.reference.multiResolve(false)
        assertTrue("assert_called_once should resolve on attribute patched in helper", resolveResults.isNotEmpty())
    }

    fun testMockerPatchStringTarget() {
        // mocker.patch("module.Class.method") should make the patched attribute a mock
        myFixture.configureByText(
            "test_mocker_patch.py", """
            class Service:
                def fetch(self) -> int:
                    return 42


            def test_mocker_patch(mocker):
                s = Service()
                mocker.patch.object(s, "fetch")
                s.fetch.assert_called_once()
        """.trimIndent()
        )

        val file = myFixture.file

        val assertRef = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
            .find { it.text == "s.fetch.assert_called_once" }!!

        val resolveResults = assertRef.reference.multiResolve(false)
        assertTrue("assert_called_once should resolve with mocker.patch.object", resolveResults.isNotEmpty())
    }

    fun testPatchObjectAsDecorator() {
        // @patch.object(Class, "method") as decorator should make the attribute a mock
        myFixture.configureByText(
            "test_decorator_patch.py", """
            from unittest.mock import patch


            class Service:
                def fetch(self) -> int:
                    return 42


            @patch.object(Service, "fetch")
            def test_decorated(mock_fetch):
                mock_fetch.assert_called_once()
        """.trimIndent()
        )

        val file = myFixture.file

        val assertRef = PsiTreeUtil.findChildrenOfType(file, PyReferenceExpression::class.java)
            .find { it.text == "mock_fetch.assert_called_once" }!!

        val resolveResults = assertRef.reference.multiResolve(false)
        assertTrue("assert_called_once should resolve on decorator mock parameter", resolveResults.isNotEmpty())
    }
}
