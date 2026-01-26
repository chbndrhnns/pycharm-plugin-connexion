package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.types.TypeEvalContext
import fixtures.TestBase

class PytestFixtureOverrideMethodsHandlerTest : TestBase() {

    fun testOverrideFixtureInModule() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return 1
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_module.py", """
            def test_something():
                pa<caret>ss
        """.trimIndent()
        )

        val handler = PytestFixtureOverrideMethodsHandler()
        val caretOffset = myFixture.editor.caretModel.offset
        val caretElement = myFixture.file.findElementAt(caretOffset) ?: myFixture.file
        val candidates = PytestFixtureOverrideUtil.collectOverridableFixtures(
            caretElement,
            TypeEvalContext.codeAnalysis(project, myFixture.file)
        )
        assertTrue("Expected base fixture candidate", candidates.any { it.fixtureName == "my_fixture" })
        handler.invoke(project, myFixture.editor, myFixture.file)

        val file = myFixture.file as PyFile
        val fixture = file.findTopLevelFunction("my_fixture")
        assertNotNull("Should create module fixture override", fixture)
        assertTrue("Fixture should have pytest decorator", fixture!!.text.contains("@pytest.fixture"))
    }

    fun testOverrideFixtureInClass() {
        myFixture.configureByText(
            "test_class.py", """
            import pytest
            
            class BaseTest:
                @pytest.fixture
                def my_fixture(self):
                    return 1
            
            class TestDerived(BaseTest):
                pa<caret>ss
        """.trimIndent()
        )

        val caretOffset = myFixture.editor.caretModel.offset
        val caretElement = myFixture.file.findElementAt(caretOffset) ?: myFixture.file
        assertNotNull("Caret element should exist", caretElement)
        assertNotNull(
            "Caret should be inside TestDerived",
            PsiTreeUtil.getParentOfType(caretElement, PyClass::class.java, false)
        )
        val handler = PytestFixtureOverrideMethodsHandler()
        val candidates = PytestFixtureOverrideUtil.collectOverridableFixtures(
            caretElement,
            TypeEvalContext.codeAnalysis(project, myFixture.file)
        )
        assertTrue("Expected base fixture candidate", candidates.any { it.fixtureName == "my_fixture" })
        handler.invoke(project, myFixture.editor, myFixture.file)

        val derivedClass = PsiTreeUtil.findChildrenOfType(myFixture.file, PyClass::class.java)
            .firstOrNull { it.name == "TestDerived" }
        assertNotNull("Should find TestDerived class", derivedClass)
        val fixture = derivedClass!!.findMethodByName("my_fixture", false, null)
        assertNotNull("Should create class fixture override", fixture)
        assertTrue("Fixture should include self parameter", fixture!!.parameterList.text.contains("self"))
    }

    fun testOverrideFixtureInNestedClass() {
        myFixture.configureByText(
            "test_nested.py", """
            import pytest
            
            class BaseTest:
                @pytest.fixture
                def my_fixture(self):
                    return 1
            
            class Outer:
                @pytest.fixture(scope="module")
                def outer_fixture(self) -> int:
                    return 2

                class Inner(BaseTest):
                    pa<caret>ss
        """.trimIndent()
        )

        val caretOffset = myFixture.editor.caretModel.offset
        val caretElement = myFixture.file.findElementAt(caretOffset) ?: myFixture.file
        val candidates = PytestFixtureOverrideUtil.collectOverridableFixtures(
            caretElement, TypeEvalContext.codeAnalysis(project, myFixture.file)
        )
        assertTrue("Expected outer class fixture candidate", candidates.any { it.fixtureName == "outer_fixture" })
        assertTrue("Expected at least one override candidate", candidates.isNotEmpty())

        val handler = PytestFixtureOverrideMethodsHandler()
        handler.invoke(project, myFixture.editor, myFixture.file)

        val innerClass = PsiTreeUtil.findChildrenOfType(myFixture.file, PyClass::class.java)
            .firstOrNull { it.name == "Inner" }
        assertNotNull("Should find Inner class", innerClass)
        val inserted =
            innerClass!!.methods.any { com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil.isFixtureFunction(it) }
        assertTrue("Should create nested class fixture override", inserted)
    }

    fun testOverrideModuleFixtureInClass() {
        myFixture.configureByText(
            "test_module_fixture.py", """
            import pytest
            
            @pytest.fixture
            def foo():
                pass
            
            class TestBla:
                <caret>
        """.trimIndent()
        )

        val targetClass = com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil
            .getContextClass(myFixture.editor, myFixture.file)
        assertNotNull("Should resolve TestBla as context class", targetClass)
        val candidates = PytestFixtureOverrideUtil.collectOverridableFixturesInClass(
            targetClass!!,
            myFixture.file as PyFile,
            TypeEvalContext.codeAnalysis(project, myFixture.file)
        )
        assertTrue("Expected module fixture candidate", candidates.any { it.fixtureName == "foo" })

        val handler = PytestFixtureOverrideMethodsHandler()
        handler.invoke(project, myFixture.editor, myFixture.file)

        val testClass = PsiTreeUtil.findChildrenOfType(myFixture.file, PyClass::class.java)
            .firstOrNull { it.name == "TestBla" }
        assertNotNull("Should find TestBla class", testClass)
        val fixture = testClass!!.findMethodByName("foo", false, null)
        assertNotNull("Should create class fixture override", fixture)
    }

    fun testOverrideCopiesDecoratorAndAnnotationsAtCaret() {
        myFixture.configureByText(
            "test_copy.py", """
            import pytest
            
            @pytest.fixture(scope="module")
            def foo() -> int:
                pass
            
            class TestBla:
                <caret>
                
                def test_(self):
                    pass
        """.trimIndent()
        )

        val handler = PytestFixtureOverrideMethodsHandler()
        handler.invoke(project, myFixture.editor, myFixture.file)

        val testClass =
            PsiTreeUtil.findChildrenOfType(myFixture.file, PyClass::class.java).firstOrNull { it.name == "TestBla" }
        assertNotNull("Should find TestBla class", testClass)

        val fixture = testClass!!.findMethodByName("foo", false, null)
        assertNotNull("Should create class fixture override", fixture)
        assertTrue("Should copy decorator parameters", fixture!!.text.contains("scope=\"module\""))
        assertTrue("Should copy return annotation", fixture.text.contains("-> int"))

        val firstStatement = testClass.statementList.statements.firstOrNull()
        assertTrue("Fixture should be inserted at caret", firstStatement is PyFunction)
    }

    fun testPopupShowsFqnForModuleFixtureAndNoDefaultOverride() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return 1
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_module.py", """
            import pytest

            def test_something():
                pa<caret>ss
        """.trimIndent()
        )

        val caretOffset = myFixture.editor.caretModel.offset
        val caretElement = myFixture.file.findElementAt(caretOffset) ?: myFixture.file
        val candidates = PytestFixtureOverrideUtil.collectOverridableFixtures(
            caretElement,
            TypeEvalContext.codeAnalysis(project, myFixture.file)
        )

        val handler = PytestFixtureOverrideMethodsHandler()
        val displayTexts = handler.buildPopupDisplayTexts(candidates, null)
        val fixture = candidates.first { it.fixtureName == "my_fixture" }.fixtureFunction
        val expectedContainer = expectedFixtureContainer(fixture)

        assertTrue(
            "Popup should include module container for fixture",
            displayTexts.contains("my_fixture ($expectedContainer)")
        )
        assertFalse(
            "Popup should not include default override outside classes",
            displayTexts.contains("Override methods...")
        )
    }

    fun testPopupShowsFqnForClassFixtureAndDefaultOverride() {
        myFixture.configureByText(
            "test_class.py", """
            import pytest
            
            class BaseTest:
                @pytest.fixture
                def my_fixture(self):
                    return 1
            
            class TestDerived(BaseTest):
                pa<caret>ss
        """.trimIndent()
        )

        val caretOffset = myFixture.editor.caretModel.offset
        val caretElement = myFixture.file.findElementAt(caretOffset) ?: myFixture.file
        val targetClass = com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil
            .getContextClass(myFixture.editor, myFixture.file)
        assertNotNull("Should resolve context class for popup", targetClass)
        val candidates = PytestFixtureOverrideUtil.collectOverridableFixtures(
            caretElement,
            TypeEvalContext.codeAnalysis(project, myFixture.file)
        )

        val handler = PytestFixtureOverrideMethodsHandler()
        val displayTexts = handler.buildPopupDisplayTexts(candidates, targetClass)
        val fixture = candidates.first { it.fixtureName == "my_fixture" }.fixtureFunction
        val expectedContainer = expectedFixtureContainer(fixture)

        assertTrue(
            "Popup should include class container for fixture",
            displayTexts.contains("my_fixture ($expectedContainer)")
        )
        assertTrue(
            "Popup should include default override inside classes",
            displayTexts.contains("Override methods...")
        )
    }

    private fun expectedFixtureContainer(function: PyFunction): String {
        val fileModule = QualifiedNameFinder.findCanonicalImportPath(function.containingFile, null)?.toString()
        val classFqn = function.containingClass?.let { cls ->
            val className = cls.name ?: "class"
            QualifiedNameFinder.findCanonicalImportPath(cls, null)?.toString()
                ?: fileModule?.let { "$it.$className" }
                ?: className
        }
        return classFqn ?: fileModule ?: "unknown"
    }
}
