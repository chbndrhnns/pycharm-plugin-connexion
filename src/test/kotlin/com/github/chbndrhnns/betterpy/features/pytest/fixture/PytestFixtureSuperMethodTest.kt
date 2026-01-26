package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext
import fixtures.TestBase

class PytestFixtureSuperMethodTest : TestBase() {

    fun testSuperFixtureFromSubclass() {
        myFixture.configureByText(
            "test_class.py", """
            import pytest
            
            class BaseTest:
                @pytest.fixture
                def my_fixture(self):
                    return "base"
            
            class TestDerived(BaseTest):
                @pytest.fixture
                def my_fi<caret>xture(self):
                    return "derived"
        """.trimIndent()
        )

        val derived = myFixture.elementAtCaret as PyFunction
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val supers = PySuperMethodsSearch.search(derived, context).findAll()

        assertTrue("Should find super fixture", supers.isNotEmpty())
        assertTrue("Should include BaseTest.my_fixture", supers.any {
            it is PyFunction && it.name == "my_fixture" && it.containingClass?.name == "BaseTest"
        })
    }

    fun testSuperFixtureFromModuleToConftest() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return "conftest"
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_module.py", """
            import pytest
            
            @pytest.fixture
            def my_fi<caret>xture():
                return "module"
        """.trimIndent()
        )

        val derived = myFixture.elementAtCaret as PyFunction
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val supers = PySuperMethodsSearch.search(derived, context).findAll()

        assertTrue("Should find super fixture", supers.isNotEmpty())
        assertTrue("Should include conftest fixture", supers.any {
            it is PyFunction && it.name == "my_fixture" && it.containingFile.name == "conftest.py"
        })
    }

    fun testSuperFixtureFromNestedConftest() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return "root"
        """.trimIndent()
        )

        val subConftest = myFixture.addFileToProject(
            "subdir/conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fi<caret>xture():
                return "subdir"
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(subConftest.virtualFile)

        val derived = myFixture.elementAtCaret as PyFunction
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val supers = PySuperMethodsSearch.search(derived, context).findAll()

        assertTrue("Should find super fixture", supers.isNotEmpty())
        assertTrue("Should include root conftest fixture", supers.any {
            it is PyFunction && it.name == "my_fixture" && it.containingFile.virtualFile.path.endsWith("/conftest.py")
                    && !it.containingFile.virtualFile.path.contains("/subdir/")
        })
    }
}
