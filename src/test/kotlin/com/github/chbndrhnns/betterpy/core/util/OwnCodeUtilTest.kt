package com.github.chbndrhnns.betterpy.core.util

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import fixtures.TestBase
import java.io.File

class OwnCodeUtilTest : TestBase() {

    fun testIsOwnCode_SourceRoot() {
        val file = myFixture.addFileToProject("src/myfile.py", "x = 1").virtualFile
        assertTrue(isOwnCode(project, file))
    }

    fun testIsOwnCode_Library() {
        // create a fake library root outside the project content
        val libDirIo = FileUtil.createTempDirectory("betterpy-lib", null, true)
        val libFileIo = File(libDirIo, "libfile.py")
        libFileIo.writeText("x = 1")
        val localFs = LocalFileSystem.getInstance()
        val libDir = localFs.refreshAndFindFileByIoFile(libDirIo)!!
        val libFile = localFs.refreshAndFindFileByIoFile(libFileIo)!!

        // Add as library
        PsiTestUtil.addProjectLibrary(module, "MyLib", libDir)

        val index = ProjectFileIndex.getInstance(project)
        val inContent = index.isInContent(libFile)
        val inLibrary = index.isInLibrary(libFile)

        // Library files should not be treated as own code.
        assertFalse(
            "Library file should not be own code. isInContent=$inContent, isInLibrary=$inLibrary",
            isOwnCode(project, libFile)
        )
    }
}
