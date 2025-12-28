package com.github.chbndrhnns.intellijplatformplugincopy.type

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import fixtures.TestBase

class PyMockTypeProviderTest : TestBase() {

    fun testMockSpecAsClass() {
        myFixture.configureByText(
            "test_mock_spec.py",
            """
            from unittest.mock import Mock

            class MyClass:
                def do_something(self) -> int: ...
                @property
                def name(self) -> str: ...

            def test_mock_spec():
                m = Mock(spec=MyClass)
                expr1 = m.do_something()
                expr2 = m.name
                
                # Check types
                print(expr1) # <caret>
            """
        )

        // We can't easily put caret at multiple places, so let's find expressions by text
        val file = myFixture.file
        val expr1 = PsiTreeUtil.findChildrenOfType(file, PyExpression::class.java)
            .find { it.text == "m.do_something()" }!!
        val expr2 = PsiTreeUtil.findChildrenOfType(file, PyExpression::class.java)
            .find { it.text == "m.name" }!!

        val context = TypeEvalContext.codeAnalysis(project, file)

        val type1 = context.getType(expr1)
        val type2 = context.getType(expr2)

        // Assertions
        assertEquals("int", type1?.name)
        assertEquals("Mock(spec=str)", type2?.name)
    }

    fun testAsyncMockSpec() {
        myFixture.configureByText(
            "test_async_mock.py",
            """
            from unittest.mock import AsyncMock
            
            class MyAsyncClass:
                async def compute(self) -> float: ...
            
            async def test_async_mock():
                m = AsyncMock(spec=MyAsyncClass)
                expr = await m.compute()
            """
        )

        val file = myFixture.file
        val expr = PsiTreeUtil.findChildrenOfType(file, PyExpression::class.java)
            .find { it.text == "await m.compute()" }!!

        val context = TypeEvalContext.codeAnalysis(project, file)
        val type = context.getType(expr)

        assertEquals("float", type?.name)
    }

    fun testSpecSet() {
        myFixture.configureByText(
            "test_spec_set.py",
            """
            from unittest.mock import Mock

            class MyClass:
                existing_attr: int

            def test_spec_set():
                m = Mock(spec_set=MyClass)
                print(m.existing_attr)
                print(m.non_existing_attr)
            """
        )

        val file = myFixture.file
        // We expect existing_attr to be resolved, and non_existing_attr to be NOT resolved (or at least return null type/not suggested)

        val exprExisting = PsiTreeUtil.findChildrenOfType(file, PyExpression::class.java)
            .find { it.text == "m.existing_attr" }!!
        val exprNonExisting = PsiTreeUtil.findChildrenOfType(file, PyExpression::class.java)
            .find { it.text == "m.non_existing_attr" }!!

        val context = TypeEvalContext.codeAnalysis(project, file)

        val typeExisting = context.getType(exprExisting)
        val typeNonExisting = context.getType(exprNonExisting)

        assertEquals("int", typeExisting?.name)
        assertNull(typeNonExisting)
    }

    fun testDeepMock() {
        myFixture.configureByText(
            "test_deep_mock.py",
            """
            from unittest.mock import Mock

            class Internal:
                def value(self) -> int: ...

            class Outer:
                def get_internal(self) -> Internal: ...

            def test_deep_mock():
                m = Mock(spec=Outer)
                res = m.get_internal().value()
            """
        )

        val file = myFixture.file
        val expr = PsiTreeUtil.findChildrenOfType(file, PyExpression::class.java)
            .find { it.text == "m.get_internal().value()" }!!

        val context = TypeEvalContext.codeAnalysis(project, file)
        val type = context.getType(expr)

        assertEquals("int", type?.name)
    }

    fun testMagicMockIterable() {
        myFixture.configureByText(
            "test_magic_mock.py",
            """
            from unittest.mock import MagicMock

            class IterableClass:
                def __iter__(self) -> object: ...

            def test_magic_mock():
                m = MagicMock(spec=IterableClass)
                for x in m:
                    pass
            """
        )

        val file = myFixture.file
        val mExpr = PsiTreeUtil.findChildrenOfType(file, PyExpression::class.java)
            .find { it.text == "m" }!!

        val context = TypeEvalContext.codeAnalysis(project, file)
        val type = context.getType(mExpr)

        assertTrue(type is PyMockType)
        // Verify we can resolve __iter__
        val resolveResult = type?.resolveMember(
            "__iter__",
            null,
            com.jetbrains.python.psi.AccessDirection.READ,
            com.jetbrains.python.psi.resolve.PyResolveContext.defaultContext(context)
        )
        assertNotNull(resolveResult)
        assertTrue(resolveResult!!.isNotEmpty())
    }
}
