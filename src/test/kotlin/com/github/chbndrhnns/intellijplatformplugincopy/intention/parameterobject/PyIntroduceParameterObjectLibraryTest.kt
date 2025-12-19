package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import fixtures.TestBase
import java.nio.file.Files

class PyIntroduceParameterObjectLibraryTest : TestBase() {

    fun testNotAvailableInLibrary() {
        val libRoot = Files.createTempDirectory("mockLib").toFile()
        VfsRootAccess.allowRootAccess(
            testRootDisposable,
            libRoot.canonicalPath,
            libRoot.path
        )

        val libFile = java.io.File(libRoot, "mylib.py")
        libFile.writeText(
            """
            def library_function(a, b):
                print(a, b)
        """.trimIndent()
        )

        val vLibRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libRoot)!!
        val vLibFile = vLibRoot.findChild("mylib.py")!!

        // Add as library
        WriteAction.run<Throwable> {
            PsiTestUtil.addLibrary(myFixture.module, "myLib", vLibRoot.path, arrayOf(), arrayOf(vLibRoot.path))
        }

        myFixture.configureFromExistingVirtualFile(vLibFile)
        myFixture.editor.caretModel.moveToOffset(
            vLibFile.contentsToByteArray().toString(Charsets.UTF_8).indexOf("library_function")
        )

        val intention = myFixture.availableIntentions.find { it.text == "BetterPy: Introduce parameter object" }
        assertNull("Intention should NOT be available in library", intention)
    }
}
