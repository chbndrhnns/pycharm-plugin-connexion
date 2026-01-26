package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.testFramework.PsiTestUtil
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PytestFixtureImplementationSearchTest : TestBase() {

    fun testShowImplementationsForClassFixture() {
        myFixture.configureByText(
            "test_class.py", """
            import pytest
            
            class BaseTest:
                @pytest.fixture
                def my_fi<caret>xture(self):
                    return "base"
            
            class TestDerived(BaseTest):
                @pytest.fixture
                def my_fixture(self):
                    return "derived"
        """.trimIndent()
        )

        val source = myFixture.elementAtCaret as PyFunction
        val targets = DefinitionsScopedSearch.search(source).findAll()

        assertTrue("Should find overriding fixture implementations", targets.isNotEmpty())
        assertTrue("Should include TestDerived.my_fixture", targets.any {
            it is PyFunction && it.name == "my_fixture" && it.containingClass?.name == "TestDerived"
        })
    }

    fun testShowImplementationsForModuleFixture() {
        myFixture.configureByText(
            "test_module.py", """
            import pytest
            
            @pytest.fixture
            def my_fi<caret>xture():
                return "module"
            
            class TestClass:
                @pytest.fixture
                def my_fixture(self):
                    return "class"
        """.trimIndent()
        )

        val source = myFixture.elementAtCaret as PyFunction
        val targets = DefinitionsScopedSearch.search(source).findAll()

        assertTrue("Should find overriding fixture implementations", targets.isNotEmpty())
        assertTrue("Should include TestClass.my_fixture", targets.any {
            it is PyFunction && it.name == "my_fixture" && it.containingClass?.name == "TestClass"
        })
    }

    fun testShowImplementationsForConftestFixture() {
        myFixture.addFileToProject(
            "subdir/conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return "subdir"
        """.trimIndent()
        )

        myFixture.addFileToProject(
            "subdir/test_example.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return "module"
        """.trimIndent()
        )

        myFixture.configureByText(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fi<caret>xture():
                return "root"
        """.trimIndent()
        )

        val source = myFixture.elementAtCaret as PyFunction
        val targets = DefinitionsScopedSearch.search(source).findAll()

        assertTrue("Should find overriding fixture implementations", targets.isNotEmpty())
        assertTrue("Should include subdir conftest fixture", targets.any {
            it is PyFunction &&
                    it.name == "my_fixture" &&
                    it.containingFile.virtualFile.path.contains("subdir/conftest.py")
        })
        assertTrue("Should include subdir test module fixture", targets.any {
            it is PyFunction &&
                    it.name == "my_fixture" &&
                    it.containingFile.name == "test_example.py"
        })
    }

    fun testShowImplementationsForConftestFixtureOutsideSourceRoots() {
        val contentRoot = myFixture.tempDirFixture.findOrCreateDir("content")
        PsiTestUtil.addContentRoot(myFixture.module, contentRoot)

        myFixture.addFileToProject("content/tests/test_f/__init__.py", "")
        myFixture.addFileToProject("content/tests/test_f/inner/__init__.py", "")
        myFixture.addFileToProject(
            "content/tests/test_f/inner/test_abc.py", """
            import pytest

            @pytest.fixture
            def my_fixture():
                return "inner"
        """.trimIndent()
        )

        val conftestFile = myFixture.addFileToProject(
            "content/tests/test_f/conftest.py", """
            import pytest

            @pytest.fixture
            def my_fixture():
                return "root"
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(conftestFile.virtualFile)
        val caretOffset = myFixture.file.text.indexOf("my_fixture") + "my_fi".length
        myFixture.editor.caretModel.moveToOffset(caretOffset)

        val source = myFixture.elementAtCaret as PyFunction
        val targets = DefinitionsScopedSearch.search(source).findAll()

        assertTrue("Should find overriding fixture implementations outside source roots", targets.isNotEmpty())
        assertTrue("Should include inner test module fixture", targets.any {
            it is PyFunction &&
                    it.name == "my_fixture" &&
                    it.containingFile.name == "test_abc.py"
        })
    }
}
