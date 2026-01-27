package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.intellij.usageView.UsageInfo
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PytestFixtureFindUsagesTest : TestBase() {

    fun testFindUsagesFromConftestFixtureIncludesOverridesAndParameterUsages() {
        myFixture.addFileToProject("tests/fixture_rename/__init__.py", "")
        myFixture.addFileToProject(
            "tests/fixture_rename/test_a.py", """
            import pytest


            @pytest.fixture
            def oldname(oldname):
                return oldname

            def test_(oldname):
                assert oldname
        """.trimIndent()
        )

        val conftestFile = myFixture.addFileToProject(
            "tests/fixture_rename/conftest.py", """
            import pytest


            @pytest.fixture
            def oldname():
                pass
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(conftestFile.virtualFile)
        val nameOffset = conftestFile.text.indexOf("oldname")
        myFixture.editor.caretModel.moveToOffset(nameOffset + 2)

        val fixtureFunction = myFixture.elementAtCaret as PyFunction
        val usages = myFixture.findUsages(fixtureFunction)
        val elements = usageElements(usages)
        assertEquals("Expected 5 usages for conftest fixture", 5, elements.size)
        assertTrue(
            "Expected all usages to be in test_a.py",
            elements.all { it.containingFile.name == "test_a.py" }
        )
    }

    fun testFindUsagesIncludesFixtureParameterAndBody() {
        myFixture.configureByText(
            "test_simple.py", """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 42

            def test_something(my_fixture):
                assert my_fixture == 42
        """.trimIndent()
        )

        val fixtureFunction = myFixture.findElementByText("def my_fixture", PyFunction::class.java)
        val usages = myFixture.findUsages(fixtureFunction)
        val elements = usageElements(usages)
        assertEquals("Expected parameter and body usage for fixture", 2, elements.size)
    }

    fun testFindUsagesIncludesUsefixturesString() {
        myFixture.configureByText(
            "test_usefixtures.py", """
            import pytest

            @pytest.fixture
            def my_fixture():
                return 42

            @pytest.mark.usefixtures("my_fixture")
            def test_something():
                pass
        """.trimIndent()
        )

        val fixtureFunction = myFixture.findElementByText("def my_fixture", PyFunction::class.java)
        val usages = myFixture.findUsages(fixtureFunction)
        val elements = usageElements(usages)
        assertEquals("Expected usefixtures string usage", 1, elements.size)
    }

    private fun usageElements(usages: Collection<UsageInfo>) =
        usages.mapNotNull { it.element }
}
