package com.github.chbndrhnns.intellijplatformplugincopy.psi

import com.jetbrains.python.psi.PyClass
import fixtures.TestBase

class PyFilterWarningsReferenceTest : TestBase() {

    override fun setUp() {
        super.setUp()
        // create pytest
        myFixture.addFileToProject(
            "pytest/__init__.py",
            "class mark:\n" +
                    "    @staticmethod\n" +
                    "    def filterwarnings(filter): pass"
        )

        // create builtins
        myFixture.addFileToProject("builtins.py", "class DeprecationWarning: pass")
    }

    fun testPytestFilterWarningsResolution() {
        // DeprecationWarning should be in builtins

        myFixture.configureByText(
            "test_warning.py", """
            import pytest
            
            @pytest.mark.filterwarnings("ignore::Depreca<caret>tionWarning")
            def test_warning():
                pass
        """.trimIndent()
        )

        val element = myFixture.getElementAtCaret()
        assertInstanceOf(element, PyClass::class.java)
        assertEquals("DeprecationWarning", (element as PyClass).name)
    }

    fun testImportedSymbolResolution() {
        myFixture.addFileToProject("source_warnings.py", "class MyWarning: pass")
        myFixture.addFileToProject("target_warnings.py", "from source_warnings import MyWarning")

        myFixture.configureByText(
            "test_imported.py", """
            import pytest
            
            @pytest.mark.filterwarnings("ignore::target_warnings.MyWar<caret>ning")
            def test_imported():
                pass
        """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPosition()
        assertNotNull(reference)
        val resolved = reference!!.resolve()
        assertNull("Should not resolve to imported symbol", resolved)
    }
}
