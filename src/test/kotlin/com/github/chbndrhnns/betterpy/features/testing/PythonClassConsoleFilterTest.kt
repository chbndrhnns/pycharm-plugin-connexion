package com.github.chbndrhnns.betterpy.features.testing

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PsiTestUtil
import fixtures.TestBase
import java.io.File
import java.nio.file.Files

class PythonClassConsoleFilterTest : TestBase() {

    fun testParseSingleQuotes() {
        val filter = PythonClassConsoleFilter(project)
        val line = "<class 'src.adapters.outbound.my_adapter.HttpAdapter'>"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(0, item.highlightStartOffset)
        assertEquals(line.length, item.highlightEndOffset)
    }

    fun testParseDoubleQuotes() {
        val filter = PythonClassConsoleFilter(project)
        val line = "<class \"src.adapters.outbound.my_adapter.HttpAdapter\">"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(0, item.highlightStartOffset)
        assertEquals(line.length, item.highlightEndOffset)
    }

    fun testParseEmbeddedInText() {
        val filter = PythonClassConsoleFilter(project)
        val prefix = "Some text before "
        val classRef = "<class 'src.MyClass'>"
        val suffix = " and after."
        val line = prefix + classRef + suffix
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
        val item = result!!.resultItems.first()
        assertEquals(prefix.length, item.highlightStartOffset)
        assertEquals(prefix.length + classRef.length, item.highlightEndOffset)
    }

    fun testNoMatch() {
        val filter = PythonClassConsoleFilter(project)
        val line = "Just some text without class ref"
        val result = filter.applyFilter(line, line.length)
        assertNull(result)
    }

    fun testResolveProjectClass() {
        myFixture.addFileToProject("src/__init__.py", "")
        myFixture.addFileToProject("src/core/__init__.py", "")
        myFixture.addFileToProject("src/core/mycore/__init__.py", "")
        myFixture.addFileToProject(
            "src/core/mycore/domain.py",
            """
            class MyRoute:
                pass
            """.trimIndent()
        )

        val element = PythonClassConsoleFilter.resolveClass(project, "src.core.mycore.domain.MyRoute")
        assertNotNull(element)
    }

    fun testResolveProjectClassWithSourceRootPrefix() {
        val srcRoot = myFixture.tempDirFixture.findOrCreateDir("src")
        runWithSourceRoots(listOf(srcRoot)) {
            myFixture.addFileToProject("src/__init__.py", "")
            myFixture.addFileToProject("src/core/__init__.py", "")
            myFixture.addFileToProject("src/core/mycore/__init__.py", "")
            myFixture.addFileToProject(
                "src/core/mycore/domain.py",
                """
                class MyRoute:
                    pass
                """.trimIndent()
            )

            val element = PythonClassConsoleFilter.resolveClass(project, "src.core.mycore.domain.MyRoute")
            assertNotNull(element)
        }
    }

    fun testResolveLibraryClass() {
        val libRoot = Files.createTempDirectory("pyclasslib").toFile()
        VfsRootAccess.allowRootAccess(
            testRootDisposable,
            libRoot.canonicalPath,
            libRoot.path
        )

        val depDir = File(libRoot, "dep")
        depDir.mkdirs()
        File(depDir, "__init__.py").writeText("")
        File(depDir, "routes.py").writeText(
            """
            class DepRoute:
                pass
            """.trimIndent()
        )

        val vLibRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libRoot)!!
        val vLibFile = vLibRoot.findFileByRelativePath("dep/routes.py")!!

        PsiTestUtil.addContentRoot(myFixture.module, vLibRoot)
        WriteAction.run<Throwable> {
            PsiTestUtil.addLibrary(
                myFixture.module,
                "depLib",
                vLibRoot.path,
                arrayOf(vLibRoot.path),
                arrayOf()
            )
        }
        WriteAction.run<Throwable> {
            val sdk = ProjectRootManager.getInstance(project).projectSdk ?: return@run
            val modificator = sdk.sdkModificator
            modificator.addRoot(vLibRoot, OrderRootType.CLASSES)
            modificator.commitChanges()
        }
        myFixture.configureFromExistingVirtualFile(vLibFile)

        val element = ReadAction.nonBlocking<PsiElement?> {
            PythonClassConsoleFilter.resolveClass(project, "dep.routes.DepRoute")
        }.inSmartMode(project).executeSynchronously()
        assertNotNull(element)
    }
}
