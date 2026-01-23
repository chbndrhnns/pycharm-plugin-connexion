package com.github.chbndrhnns.betterpy.features.imports

import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyFile
import fixtures.TestBase

class SpecificImportPathProviderTest : TestBase() {

    fun testPreferSpecificPathOverPackagePath() {
        // 1. Create structure
        // src/domain/_lib.py defining Foo
        val libFile = myFixture.addFileToProject("src/domain/_lib.py", "class Foo: pass") as PyFile
        val fooClass = libFile.findTopLevelClass("Foo")!!

        // src/domain/__init__.py re-exporting Foo (so 'domain' is a candidate)
        myFixture.addFileToProject("src/domain/__init__.py", "from ._lib import Foo")

        // src/usecase.py (foothold)
        val usecaseFile = myFixture.addFileToProject("src/usecase.py", "")

        // 2. Manage source root lifecycle
        val srcDir = usecaseFile.virtualFile.parent
        runWithSourceRoots(listOf(srcDir)) {
            // 3. Test the provider directly
            val provider = SpecificImportPathProvider()

            // The default resolution (via __init__.py re-export) would suggest 'domain' as the module path for Foo.
            // We simulate this by passing 'domain' as the proposed qName.
            val proposedQName = QualifiedName.fromDottedString("domain")

            // We expect the provider to override this with the specific path 'domain._lib'.
            val result = provider.getCanonicalPath(fooClass, proposedQName, usecaseFile)

            assertNotNull("Provider should return a specific path", result)
            assertEquals("domain._lib", result!!.toString())
        }
    }
}
