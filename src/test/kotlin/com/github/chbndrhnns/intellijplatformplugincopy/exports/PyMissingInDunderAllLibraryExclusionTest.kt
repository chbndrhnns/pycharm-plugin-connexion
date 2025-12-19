package com.github.chbndrhnns.intellijplatformplugincopy.exports

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import fixtures.TestBase
import java.nio.file.Files

class PyMissingInDunderAllLibraryExclusionTest : TestBase() {

    fun testInspectionDoesNotRunInLibraries() {
        // Create a mock library with a package that has missing __all__ entries
        val libRoot = Files.createTempDirectory("mockLib").toFile()
        VfsRootAccess.allowRootAccess(testRootDisposable, libRoot.canonicalPath, libRoot.path)

        val pkgDir = libRoot.resolve("libpkg").apply { mkdirs() }
        val initPy = pkgDir.resolve("__init__.py")
        val modulePy = pkgDir.resolve("module.py")
        initPy.writeText(
            """
            # package init without __all__
            from .module import PublicCls
        """.trimIndent()
        )
        modulePy.writeText(
            """
            class PublicCls:
                pass
        """.trimIndent()
        )

        val vLibRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libRoot)!!

        // Add as content root so it is considered "project code" by default (reproduces isInContent=true)
        PsiTestUtil.addContentRoot(myFixture.module, vLibRoot)

        // Add as a library (so it should be excluded by the fix)
        PsiTestUtil.addLibrary(myFixture.module, "mock-lib", vLibRoot.path, arrayOf(""), arrayOf(""))

        // Open the library file
        val vInitPy = vLibRoot.findFileByRelativePath("libpkg/__init__.py")!!
        myFixture.configureFromExistingVirtualFile(vInitPy)

        // Enable inspection and verify there are no highlightings
        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testPrivateModuleImportInspectionDoesNotRunInLibraries() {
        // Create a mock library with a private module import issue
        val libRoot = Files.createTempDirectory("mockLib2").toFile()
        VfsRootAccess.allowRootAccess(testRootDisposable, libRoot.canonicalPath, libRoot.path)

        val pkgDir = libRoot.resolve("libpkg2").apply { mkdirs() }
        val initPy = pkgDir.resolve("__init__.py")
        val privateMod = pkgDir.resolve("_module.py")
        // client.py is outside the package to trigger inspection
        val clientPy = libRoot.resolve("client.py")

        initPy.writeText(
            """
            __all__ = ["Foo"]
            from ._module import Foo
        """.trimIndent()
        )
        privateMod.writeText(
            """
            class Foo: pass
        """.trimIndent()
        )
        clientPy.writeText(
            """
            from libpkg2._module import Foo
        """.trimIndent()
        )

        val vLibRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libRoot)!!

        // Add as content root
        PsiTestUtil.addContentRoot(myFixture.module, vLibRoot)

        // Add as library
        PsiTestUtil.addLibrary(myFixture.module, "mock-lib-2", vLibRoot.path, arrayOf(""), arrayOf(""))

        val vClientPy = vLibRoot.findFileByRelativePath("client.py")!!
        myFixture.configureFromExistingVirtualFile(vClientPy)

        myFixture.enableInspections(PyPrivateModuleImportInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }
}
