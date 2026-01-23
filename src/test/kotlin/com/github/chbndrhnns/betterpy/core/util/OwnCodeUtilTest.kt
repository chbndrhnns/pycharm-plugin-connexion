package com.github.chbndrhnns.betterpy.core.util

import com.intellij.testFramework.PsiTestUtil
import fixtures.TestBase

class OwnCodeUtilTest : TestBase() {

    fun testIsOwnCode_SourceRoot() {
        val file = myFixture.addFileToProject("src/myfile.py", "x = 1").virtualFile
        assertTrue(isOwnCode(project, file))
    }

    fun testIsOwnCode_Library() {
        // create a fake library root
        val libDir = myFixture.tempDirFixture.findOrCreateDir("lib")
        val libFile = myFixture.addFileToProject("lib/libfile.py", "x = 1").virtualFile

        // Add as library
        PsiTestUtil.addProjectLibrary(module, "MyLib", libDir)

        val index = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
        val inContent = index.isInContent(libFile)
        val inLibrary = index.isInLibrary(libFile)

        // Should NOT be own code
        assertFalse(
            "Library file should not be own code. isInContent=$inContent, isInLibrary=$inLibrary",
            isOwnCode(project, libFile)
        )
    }
}
