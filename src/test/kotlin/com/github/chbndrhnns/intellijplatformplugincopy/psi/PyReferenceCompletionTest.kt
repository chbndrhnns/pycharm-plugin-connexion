package com.github.chbndrhnns.intellijplatformplugincopy.psi

import fixtures.TestBase

class PyReferenceCompletionTest : TestBase() {

    fun testModuleCompletionDoesNotShowExtension() {
        myFixture.addFileToProject("pkg/__init__.py", "")
        myFixture.addFileToProject("pkg/mod.py", "def foo(): pass")
        myFixture.addFileToProject("pkg/mod2.py", "def bar(): pass")

        myFixture.configureByText(
            "test_completion.py", """
            from unittest.mock import patch
            
            @patch('pkg.<caret>')
            def test_something(mock):
                pass
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_completion.py")
        assertNotNull(variants)

        // We expect "mod", not "mod.py"
        assertTrue("Should contain 'mod', but found: $variants", variants!!.contains("mod"))
        assertFalse("Should not contain 'mod.py', but found: $variants", variants.contains("mod.py"))
    }
}
