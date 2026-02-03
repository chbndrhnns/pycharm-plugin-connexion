package com.github.chbndrhnns.betterpy.core.psi

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.refactoring.rename.RenameProcessor
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class PyMockPatchReferenceTest : TestBase() {

    override fun setUp() {
        super.setUp()
        // Setup stubs
        // create unittest/mock.py
        myFixture.addFileToProject("unittest/mock.py", "def patch(target, new=None): pass")
        myFixture.addFileToProject("unittest/__init__.py", "")
    }

    fun testMockPatchResolution() {
        myFixture.addFileToProject("os/path.py", "def exists(path): pass")
        myFixture.addFileToProject("os/__init__.py", "")

        myFixture.configureByText(
            "test_mock_patch.py", """
            from unittest.mock import patch
            import os.path
            
            @patch('os.path.ex<caret>ists') 
            def test_something(mock_exists):
                pass
        """.trimIndent()
        )

        val element = myFixture.elementAtCaret
        assertInstanceOf(element, PyFunction::class.java)
        assertEquals("exists", (element as PyFunction).name)
    }

    fun testMockPatchClassResolution() {
        myFixture.addFileToProject(
            "MyModule.py", """
            class MyClass:
                def method(self): pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_foo.py", """
            from unittest import mock
            
            def test_foo():
                with mock.patch('MyModule.MyCla<caret>ss'):
                    pass
        """.trimIndent()
        )

        val element = myFixture.elementAtCaret
        assertInstanceOf(element, PyClass::class.java)
        assertEquals("MyClass", (element as PyClass).name)
    }

    fun testRenameModule() {
        myFixture.addFileToProject(
            "old_module.py", """
            class MyClass:
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_rename_mod.py", """
            from unittest.mock import patch
            
            @patch('old_module.MyClass')
            def test_something(mock_cls):
                pass
        """.trimIndent()
        )

        val moduleFile = myFixture.findFileInTempDir("old_module.py")
        val psiFile = myFixture.psiManager.findFile(moduleFile)!!
        myFixture.renameElement(psiFile, "new_module.py")

        myFixture.checkResult(
            """
            from unittest.mock import patch
            
            @patch('new_module.MyClass')
            def test_something(mock_cls):
                pass
        """.trimIndent()
        )
    }

    fun testRename() {
        myFixture.addFileToProject(
            "RenameModule.py", """
            class OldClass:
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_rename.py", """
            from unittest.mock import patch
            
            @patch('RenameModule.Old<caret>Class')
            def test_something(mock_cls):
                pass
        """.trimIndent()
        )

        myFixture.renameElementAtCaret("NewClass")
        myFixture.checkResult(
            """
            from unittest.mock import patch
            
            @patch('RenameModule.NewClass')
            def test_something(mock_cls):
                pass
        """.trimIndent()
        )
    }

    fun testRenameWithSearchInStringsDisabledStillUpdatesPatch() {
        myFixture.addFileToProject(
            "RenameModule.py", """
            class OldClass:
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_rename_settings.py", """
            from unittest.mock import patch

            @patch('RenameModule.Old<caret>Class')
            def test_something(mock_cls):
                pass
        """.trimIndent()
        )

        val element = myFixture.elementAtCaret
        assertInstanceOf(element, PyClass::class.java)

        RenameProcessor(project, element, "NewClass", false, false).run()

        myFixture.checkResult(
            """
            from unittest.mock import patch

            @patch('RenameModule.NewClass')
            def test_something(mock_cls):
                pass
        """.trimIndent()
        )
    }

    fun testCompletion() {
        myFixture.addFileToProject(
            "CompletionModule.py", """
            class TargetClass:
                pass
            def target_func():
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_completion.py", """
            from unittest.mock import patch
            
            @patch('CompletionModule.<caret>')
            def test_something(mock_cls):
                pass
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_completion.py")
        assertNotNull(variants)
        assertTrue(variants!!.contains("TargetClass"))
        assertTrue(variants.contains("target_func"))
    }

    fun testCompletionRespectsSourceRootPrefix() {
        myFixture.addFileToProject("tests/__init__.py", "")
        myFixture.addFileToProject(
            "tests/test_.py", """
            class Class:
                def method(self):
                    pass
        """.trimIndent()
        )

        runWithSourceRoots(listOf(myFixture.findFileInTempDir("tests")!!)) {
            val prefixFile = myFixture.addFileToProject(
                "tests/patch_prefix.py",
                """
                from unittest.mock import patch

                with patch(''):
                    pass
            """.trimIndent()
            )

            myFixture.configureFromExistingVirtualFile(prefixFile.virtualFile)
            myFixture.editor.caretModel.moveToOffset(
                myFixture.editor.document.text.indexOf("patch('") + "patch('".length
            )

            val prefixVariants = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
            assertTrue(prefixVariants.contains("tests"))

            val moduleFile = myFixture.addFileToProject(
                "tests/patch_module.py",
                """
                from unittest.mock import patch

                with patch('tests.'):
                    pass
            """.trimIndent()
            )

            myFixture.configureFromExistingVirtualFile(moduleFile.virtualFile)
            myFixture.editor.caretModel.moveToOffset(
                myFixture.editor.document.text.indexOf("patch('") + "patch('".length + "tests.".length
            )

            val moduleVariants = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
            assertTrue("Module variants: $moduleVariants", moduleVariants.contains("test_"))

            val symbolFile = myFixture.addFileToProject(
                "tests/patch_symbol.py",
                """
                from unittest.mock import patch

                with patch('tests.test_.'):
                    pass
            """.trimIndent()
            )

            myFixture.configureFromExistingVirtualFile(symbolFile.virtualFile)
            myFixture.editor.caretModel.moveToOffset(
                myFixture.editor.document.text.indexOf("patch('") + "patch('".length + "tests.test_.".length
            )

            val symbolVariants = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
            assertTrue("Symbol variants: $symbolVariants", symbolVariants.contains("Class"))
        }
    }

    fun testReferenceWithoutSourceRootPrefixIsUnresolvedWhenPrefixRequired() {
        myFixture.addFileToProject("tests/__init__.py", "")
        myFixture.addFileToProject(
            "tests/test_.py", """
            class Class:
                pass
        """.trimIndent()
        )

        runWithSourceRoots(listOf(myFixture.findFileInTempDir("tests")!!)) {
            myFixture.configureByText(
                "test_unresolved_prefix_required.py",
                """
                from unittest.mock import patch

                with patch('test_.Cla<caret>ss'):
                    pass
            """.trimIndent()
            )

            val ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset) as? PsiPolyVariantReference
            assertNotNull(ref)
            assertEmpty(ref!!.multiResolve(false))
        }
    }

    fun testReferenceWithSourceRootPrefixIsUnresolvedWhenPrefixDisabled() {
        myFixture.addFileToProject("tests/__init__.py", "")
        myFixture.addFileToProject(
            "tests/test_.py", """
            class Class:
                pass
        """.trimIndent()
        )

        withPluginSettings({ enableRestoreSourceRootPrefix = false }) {
            runWithSourceRoots(listOf(myFixture.findFileInTempDir("tests")!!)) {
                myFixture.configureByText(
                    "test_unresolved_prefix_disabled.py",
                    """
                    from unittest.mock import patch

                    with patch('tests.test_.Cla<caret>ss'):
                        pass
                """.trimIndent()
                )

                val ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset) as? PsiPolyVariantReference
                assertNotNull(ref)
                assertEmpty(ref!!.multiResolve(false))
            }
        }
    }

    fun testResolveImportedSymbol() {
        // Define the symbol that will be imported
        myFixture.addFileToProject("source_module.py", "class MyImportedClass: pass")

        // Define the module that imports it
        myFixture.addFileToProject("target_module.py", "from source_module import MyImportedClass")

        // Test patching the imported symbol in the target module
        myFixture.configureByText(
            "test_patch_imported.py", """
            from unittest.mock import patch
            
            @patch('target_module.MyImported<caret>Class')
            def test_something(mock_cls):
                pass
        """.trimIndent()
        )

        val element = myFixture.elementAtCaret
        assertInstanceOf(element, PyClass::class.java)
        assertEquals("MyImportedClass", (element as PyClass).name)
    }

    fun testResolveAttributeOfImportedSymbol() {
        // pathlib.py
        myFixture.addFileToProject(
            "pathlib.py", """
            class Path:
                def read_text(self): pass
        """.trimIndent()
        )

        // mymodule.py
        myFixture.addFileToProject(
            "mymodule.py", """
            from pathlib import Path
            
            def read():
                return Path().read_text()
        """.trimIndent()
        )

        // test.py
        myFixture.configureByText(
            "test_patch_attr.py", """
            from unittest import mock
            
            # 1. Where it's used
            mock.patch("mymodule.Path.read_te<caret>xt")
        """.trimIndent()
        )

        val element1 = myFixture.elementAtCaret
        assertInstanceOf(element1, PyFunction::class.java)
        assertEquals("read_text", (element1 as PyFunction).name)

        // 2. At the source
        myFixture.configureByText(
            "test_patch_attr_source.py", """
            from unittest import mock
            
            mock.patch("pathlib.Path.read_te<caret>xt")
        """.trimIndent()
        )

        val element2 = myFixture.elementAtCaret
        assertInstanceOf(element2, PyFunction::class.java)
        assertEquals("read_text", (element2 as PyFunction).name)
    }
}
