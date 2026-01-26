package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PytestFixtureGotoSuperHandlerTest : TestBase() {

    fun testGotoSuperNavigatesToConftestParent() {
        addConftestBaseFixture()

        val testFile = myFixture.addFileToProject(
            "tests/fixtures/test_base.py", """
            import pytest

            @pytest.fixture
            def ba<caret>se():
                ...
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(testFile.virtualFile)

        val handler = PytestFixtureGotoSuperHandler()
        handler.invoke(project, myFixture.editor, myFixture.file)

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        assertNotNull("Expected editor to navigate to conftest.py", editor)

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor!!.document)
        assertNotNull("Expected psi file for navigated editor", psiFile)
        assertEquals("conftest.py", psiFile!!.name)
        assertTrue(
            "Expected to navigate to tests/fixtures/conftest.py",
            psiFile.virtualFile.path.endsWith("/tests/fixtures/conftest.py")
        )
    }

    fun testGotoSuperFromFixtureParameterInTest() {
        addConftestBaseFixture()

        val testFile = myFixture.addFileToProject(
            "tests/fixtures/test_usage.py", """
            def test_base(ba<caret>se):
                assert base
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(testFile.virtualFile)

        val handler = PytestFixtureGotoSuperHandler()
        handler.invoke(project, myFixture.editor, myFixture.file)

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        assertNotNull("Expected editor to navigate to conftest.py", editor)

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor!!.document)
        assertNotNull("Expected psi file for navigated editor", psiFile)
        assertEquals("conftest.py", psiFile!!.name)
    }

    fun testGotoSuperFromFixtureUsageInTestBody() {
        addConftestBaseFixture()

        val testFile = myFixture.addFileToProject(
            "tests/fixtures/test_usage_in_body.py", """
            def test_base(base):
                assert ba<caret>se
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(testFile.virtualFile)

        val handler = PytestFixtureGotoSuperHandler()
        handler.invoke(project, myFixture.editor, myFixture.file)

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        assertNotNull("Expected editor to navigate to conftest.py", editor)

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor!!.document)
        assertNotNull("Expected psi file for navigated editor", psiFile)
        assertEquals("conftest.py", psiFile!!.name)
    }

    fun testPopupDisplaysFixtureContainer() {
        val testFile = myFixture.addFileToProject(
            "test_nested.py", """
            import pytest

            @pytest.fixture
            def base():
                ...

            class TestCase:
                @pytest.fixture
                def base(self):
                    ...
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(testFile.virtualFile)

        val functions = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java)
        val moduleFixture = functions.firstOrNull { it.containingClass == null }
        val classFixture = functions.firstOrNull { it.containingClass != null }
        assertNotNull("Should find module-level fixture", moduleFixture)
        assertNotNull("Should find class fixture", classFixture)

        val handler = PytestFixtureGotoSuperHandler()
        val displayTexts = handler.buildPopupDisplayTexts(
            listOf(
                FixtureLink(moduleFixture!!, "base"),
                FixtureLink(classFixture!!, "base")
            )
        )

        assertTrue(
            "Popup should include module container for fixture", displayTexts.contains("base (test_nested)")
        )
        assertTrue(
            "Popup should include class container for fixture", displayTexts.contains("base (test_nested.TestCase)")
        )
    }

    private fun addConftestBaseFixture() {
        myFixture.addFileToProject(
            "tests/fixtures/conftest.py", """
            import pytest

            @pytest.fixture
            def base():
                ...
        """.trimIndent()
        )
    }
}
