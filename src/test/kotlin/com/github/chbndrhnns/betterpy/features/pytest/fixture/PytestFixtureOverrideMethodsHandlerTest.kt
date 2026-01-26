package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyImportStatement
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
            <caret>
            def test_something():
                pass
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
                pass
                <caret>
        """.trimIndent()
        )

        val caretOffset = myFixture.editor.caretModel.offset
        val caretElement = myFixture.file.findElementAt(caretOffset) ?: myFixture.file
        assertNotNull("Caret element should exist", caretElement)
        
        val targetClass = com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil
            .getContextClass(myFixture.editor, myFixture.file)
        assertNotNull("Caret should be inside TestDerived (context class)", targetClass)
        assertEquals("TestDerived", targetClass?.name)

        val handler = PytestFixtureOverrideMethodsHandler()
        val candidates = PytestFixtureOverrideUtil.collectOverridableFixturesInClass(
            targetClass!!,
            myFixture.file as PyFile,
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

    fun testOverrideFixtureInsertsImportsBeforeFixtureAtTopOfFile() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return 1
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_import_order.py", """
            <caret>
        """.trimIndent()
        )

        val handler = PytestFixtureOverrideMethodsHandler()
        handler.invoke(project, myFixture.editor, myFixture.file)

        val file = myFixture.file as PyFile
        val fixture = file.findTopLevelFunction("my_fixture")
        val importStatement = file.importBlock.filterIsInstance<PyImportStatement>().firstOrNull()

        assertNotNull("Should insert pytest import", importStatement)
        assertNotNull("Should create module fixture override", fixture)
        assertTrue(
            "Import should be before fixture",
            importStatement!!.textRange.startOffset < fixture!!.textRange.startOffset
        )
    }

    fun testOverrideFixtureCreatesNoOpBodyWithFixtureParam() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture(dep):
                return 1
            
            @pytest.fixture
            def dep():
                ...
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_noop_override.py", """
            <caret>
        """.trimIndent()
        )

        val handler = PytestFixtureOverrideMethodsHandler()
        handler.invoke(project, myFixture.editor, myFixture.file)

        checkResult(
            """
            |import pytest
            |
            |
            |@pytest.fixture
            |def my_fixture(my_fixture, dep):
            |    return my_fixture
            """
        )
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
                    <caret>
                    pass
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

        checkResult(
            """
            import pytest
            
            @pytest.fixture(scope="module")
            def foo() -> int:
                pass
            
            class TestBla:
                
                
                @pytest.fixture(scope="module")
                def foo(self, foo) -> int:
                    return foo
            
                def test_(self):
                    pass
            """.trimIndent()
        )
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

            <caret>
            def test_something():
                pass
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
                pass
                <caret>
        """.trimIndent()
        )

        val caretOffset = myFixture.editor.caretModel.offset
        val targetClass = com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil
            .getContextClass(myFixture.editor, myFixture.file)
        assertNotNull("Should resolve context class for popup", targetClass)
        val candidates = PytestFixtureOverrideUtil.collectOverridableFixturesInClass(
            targetClass!!,
            myFixture.file as PyFile,
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

    fun testPopupShowsFqnForNestedClassFixture() {
        myFixture.configureByText(
            "test_nested.py", """
            import pytest
            
            class TestBla:
                class TestInner:
                    @pytest.fixture
                    def foo(self):
                        return 1
        """.trimIndent()
        )

        val innerClass = PsiTreeUtil.findChildrenOfType(myFixture.file, PyClass::class.java)
            .firstOrNull { it.name == "TestInner" }
        assertNotNull("Should find inner class", innerClass)
        val fixture = innerClass!!.findMethodByName("foo", false, null)
        assertNotNull("Should find nested class fixture", fixture)

        val handler = PytestFixtureOverrideMethodsHandler()
        val candidates = listOf(FixtureLink(fixture!!, "foo"))
        val displayTexts = handler.buildPopupDisplayTexts(candidates, null)
        val expectedContainer = expectedFixtureContainer(fixture)

        assertTrue(
            "Popup should include nested class container for fixture",
            displayTexts.contains("foo ($expectedContainer)")
        )
    }

    private fun expectedFixtureContainer(function: PyFunction): String {
        val fileModule = QualifiedNameFinder.findCanonicalImportPath(function.containingFile, null)?.toString()
        val classFqn = function.containingClass?.let { cls ->
            val classChain = buildClassChain(cls)
            fileModule?.let { "$it.$classChain" } ?: classChain
        }
        return classFqn ?: fileModule ?: "unknown"
    }

    private fun buildClassChain(cls: PyClass): String {
        val classNames = mutableListOf<String>()
        var current: PyClass? = cls
        while (current != null) {
            classNames.add(current.name ?: "class")
            current = PsiTreeUtil.getParentOfType(current, PyClass::class.java, true)
        }
        return classNames.asReversed().joinToString(".")
    }

    private fun checkResult(expected: String) {
        var normalized = expected.trimMargin()
        if (!normalized.endsWith("\n") && myFixture.file.text.endsWith("\n")) {
            normalized += "\n"
        }
        assertEquals(normalized, myFixture.file.text)
    }
}
