package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import fixtures.TestBase

class PytestFixtureGotoSuperHandlerTest : TestBase() {

    fun testGotoSuperNavigatesToConftestParent() {
        myFixture.addFileToProject(
            "tests/fixtures/conftest.py", """
            import pytest

            @pytest.fixture
            def base():
                ...
        """.trimIndent()
        )

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
}
