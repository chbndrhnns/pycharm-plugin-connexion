package com.github.chbndrhnns.intellijplatformplugincopy.features.exports

import fixtures.TestBase

class PyPrivateModuleImportInspectionReproductionTest : TestBase() {

    fun testIgnorePrivatePackage() {
        // myp/_priv/_impl.py contains Foo
        // myp/_priv/__init__.py exists (but empty)
        // main.py imports Foo from _impl
        // Since _priv is private, we should NOT suggest adding to _priv/__all__

        myFixture.addFileToProject("myp/__init__.py", "")
        myFixture.addFileToProject("myp/_priv/__init__.py", "")
        myFixture.addFileToProject("myp/_priv/_impl.py", "class Foo: pass")

        val file = myFixture.addFileToProject("main.py", "from myp._priv._impl import Foo")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        myFixture.enableInspections(PyPrivateModuleImportInspection::class.java)
        myFixture.doHighlighting()

        val fixes = myFixture.getAllQuickFixes()
        assertTrue(
            "Should not suggest adding to __all__ of a private package",
            fixes.none { it.familyName.contains("Make symbol public") }
        )
    }

    fun testSuggestPublicPackageIfAvailable() {
        // myp exports Foo
        // myp/_priv/_impl.py contains Foo
        // main.py imports Foo from _priv/_impl
        // Should suggest importing from myp

        myFixture.addFileToProject("myp/__init__.py", "from ._priv._impl import Foo\n__all__ = ['Foo']")
        myFixture.addFileToProject("myp/_priv/__init__.py", "")
        myFixture.addFileToProject("myp/_priv/_impl.py", "class Foo: pass")

        val file = myFixture.addFileToProject("main.py", "from myp._priv._impl import Foo")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        myFixture.enableInspections(PyPrivateModuleImportInspection::class.java)
        myFixture.doHighlighting()

        val fixes = myFixture.getAllQuickFixes()
        // Check for the fix that suggests importing from 'myp'
        // The message is "Symbol 'Foo' is exported from package __all__; import it from the package instead of the private module"
        // But wait, which package? The current code only looks at the immediate parent package.
        // If the immediate parent is _priv, it checks _priv/__all__.

        // We want it to find 'myp'.

        val fix = fixes.find { it.familyName == "Use exported symbol from package instead of private module" }
        assertNotNull("Should suggest using exported symbol from public package", fix)
    }
}
