package com.github.chbndrhnns.intellijplatformplugincopy.psi

import com.github.chbndrhnns.intellijplatformplugincopy.inspections.PyMockPatchUnresolvedReferenceInspection
import fixtures.TestBase

class PyMockPatchFQNQuickFixTest : TestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("unittest/mock.py", "def patch(target, new=None): pass")
        myFixture.addFileToProject("unittest/__init__.py", "")
        myFixture.enableInspections(PyMockPatchUnresolvedReferenceInspection::class.java)
    }

    fun testFQNQuickFixForPatchTarget() {
        myFixture.addFileToProject(
            "my_module.py", """
            class MyClass:
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_fix.py", """
            from unittest.mock import patch
            
            @patch('MyCla<caret>ss')
            def test_something(m):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Replace")
        val intention = intentions.find { it.text.contains("Replace 'MyClass' with '") && it.text.contains(".MyClass'") }
        assertNotNull("Replace quickfix should be available for 'MyClass'", intention)
        
        myFixture.launchAction(intention!!)
        val stringLiteral = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), com.jetbrains.python.psi.PyStringLiteralExpression::class.java)
        assertNotNull("String literal should be found", stringLiteral)
        val result = stringLiteral!!.stringValue
        assertTrue("FQN should contain 'my_module.MyClass', but was '$result'", result.contains("my_module.MyClass"))
    }

    fun testFQNQuickFixForPatchTargetFunction() {
        myFixture.addFileToProject(
            "my_funcs.py", """
            def my_func():
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_fix_func.py", """
            from unittest.mock import patch
            
            @patch('my_fun<caret>c')
            def test_something(m):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Replace")
        val intention = intentions.find { it.text.contains("Replace 'my_func' with '") && it.text.contains(".my_func'") }
        assertNotNull("Replace quickfix should be available for 'my_func'", intention)

        myFixture.launchAction(intention!!)
        val stringLiteral = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), com.jetbrains.python.psi.PyStringLiteralExpression::class.java)
        assertNotNull("String literal should be found", stringLiteral)
        val result = stringLiteral!!.stringValue
        assertTrue("FQN should contain 'my_funcs.my_func', but was '$result'", result.contains("my_funcs.my_func"))
    }

    fun testFQNQuickFixForPatchTargetModule() {
        // Use a module name that is definitely not in the current project yet
        myFixture.addFileToProject("pkg/__init__.py", "")
        myFixture.addFileToProject("pkg/another_mod.py", "class SomeClass: pass")

        myFixture.configureByText(
            "test_fix_mod.py", """
            from unittest.mock import patch
            
            @patch('another_mo<caret>d.SomeClass')
            def test_something(m):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Replace")
        val intention = intentions.find { it.text.contains("Replace 'another_mod' with '") && it.text.contains("another_mod'") }
        assertNotNull("Replace quickfix should be available for 'another_mod'", intention)

        myFixture.launchAction(intention!!)
        val stringLiteral = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), com.jetbrains.python.psi.PyStringLiteralExpression::class.java)
        assertNotNull("String literal should be found", stringLiteral)
        val result = stringLiteral!!.stringValue
        assertTrue("FQN should contain 'pkg.another_mod.SomeClass', but was '$result'", result.contains("pkg.another_mod.SomeClass"))
    }

    fun testFQNQuickFixRespectsSourceRootPrefix() {
        myFixture.addFileToProject("src/pkg/__init__.py", "")
        myFixture.addFileToProject("src/pkg/mod.py", "class MyClass: pass")

        // src is a source root
        runWithSourceRoots(listOf(myFixture.findFileInTempDir("src")!!)) {
            // By default enableRestoreSourceRootPrefix is true in TestBase? 
            // Let's check PluginSettingsState default or TestBase.setUp
            
            myFixture.configureByText(
                "test_prefix.py", """
                from unittest.mock import patch
                
                @patch('MyCla<caret>ss')
                def test_something(m):
                    pass
            """.trimIndent()
            )

            val intentions = myFixture.filterAvailableIntentions("Replace")
            val intention = intentions.find { it.text.contains("Replace 'MyClass' with '") }
            assertNotNull("Replace quickfix should be available", intention)
            
            myFixture.launchAction(intention!!)
            val stringLiteral = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), com.jetbrains.python.psi.PyStringLiteralExpression::class.java)
            val result = stringLiteral!!.stringValue
            
            // If enableRestoreSourceRootPrefix is true, it should probably be 'src.pkg.mod.MyClass'
            // if we want to match how PyResolveUtils resolves it.
            assertTrue("FQN should contain 'src.pkg.mod.MyClass', but was '$result'", result.contains("src.pkg.mod.MyClass"))
        }
    }

    fun testFQNQuickFixRespectsSourceRootPrefixDisabled() {
        myFixture.addFileToProject("src/pkg/__init__.py", "")
        myFixture.addFileToProject("src/pkg/mod.py", "class MyClass: pass")

        // src is a source root
        runWithSourceRoots(listOf(myFixture.findFileInTempDir("src")!!)) {
            com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState.instance().state.enableRestoreSourceRootPrefix = false
            try {
                myFixture.configureByText(
                    "test_prefix_disabled.py", """
                    from unittest.mock import patch
                    
                    @patch('MyCla<caret>ss')
                    def test_something(m):
                        pass
                """.trimIndent()
                )

                val intentions = myFixture.filterAvailableIntentions("Replace")
                val intention = intentions.find { it.text.contains("Replace 'MyClass' with '") }
                assertNotNull("Replace quickfix should be available", intention)

                myFixture.launchAction(intention!!)
                val stringLiteral = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), com.jetbrains.python.psi.PyStringLiteralExpression::class.java)
                val result = stringLiteral!!.stringValue

                // If enableRestoreSourceRootPrefix is false, it should be 'pkg.mod.MyClass'
                assertTrue("FQN should contain 'pkg.mod.MyClass', but was '$result'", result.contains("pkg.mod.MyClass"))
                assertFalse("FQN should NOT contain 'src.pkg.mod.MyClass', but was '$result'", result.contains("src.pkg.mod.MyClass"))
            } finally {
                com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState.instance().state.enableRestoreSourceRootPrefix = true
            }
        }
    }

    fun testFQNQuickFixMultipleCandidates() {
        myFixture.addFileToProject("pkg1/mod.py", "class Target: pass")
        myFixture.addFileToProject("pkg2/mod.py", "class Target: pass")

        myFixture.configureByText(
            "test_multiple.py", """
            from unittest.mock import patch
            
            @patch('Tar<caret>get')
            def test_something(m):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Replace 'Target' with FQN...")
        assertNotNull("Multiple candidates quickfix should be available", intentions.find { it.text == "Replace 'Target' with FQN..." })
    }
}
