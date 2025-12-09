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
}
