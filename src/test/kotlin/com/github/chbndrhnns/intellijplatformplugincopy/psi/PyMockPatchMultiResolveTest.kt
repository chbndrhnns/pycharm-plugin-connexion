package com.github.chbndrhnns.intellijplatformplugincopy.psi

import com.intellij.psi.PsiPolyVariantReference
import com.jetbrains.python.psi.PyClass
import fixtures.TestBase

class PyMockPatchMultiResolveTest : TestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("unittest/mock.py", "def patch(target, new=None): pass")
        myFixture.addFileToProject("unittest/__init__.py", "")
    }

    fun testMultiResolveAmbiguousImport() {
        fixtures.SettingsTestUtils.withPluginSettings({ enableRestoreSourceRootPrefix = false }) {
            myFixture.addFileToProject("root1/pkg/__init__.py", "")
            myFixture.addFileToProject("root1/pkg/mod.py", "class MyClass: pass")
            myFixture.addFileToProject("root2/pkg/__init__.py", "")
            myFixture.addFileToProject("root2/pkg/mod.py", "class MyClass: pass")

            runWithSourceRoots(
                listOf(
                    myFixture.findFileInTempDir("root1")!!,
                    myFixture.findFileInTempDir("root2")
                )
            ) {
                myFixture.configureByText(
                    "test_ambiguous.py", """
                    from unittest.mock import patch
                    import pkg.mod
                    
                    @patch('pkg.mod.MyCl<caret>ass')
                    def test_something(m):
                        pass
                """.trimIndent()
                )

                val ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset) as PsiPolyVariantReference
                val results = ref.multiResolve(false)

                assertEquals("Should resolve to 2 candidates due to multiple source roots", 2, results.size)

                results.forEach {
                    val element = it.element
                    assertInstanceOf(element, PyClass::class.java)
                    assertEquals("MyClass", (element as PyClass).name)
                }
            }
        }
    }
}
