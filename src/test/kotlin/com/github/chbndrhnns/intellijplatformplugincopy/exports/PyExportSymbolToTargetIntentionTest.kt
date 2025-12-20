package com.github.chbndrhnns.intellijplatformplugincopy.exports

import com.jetbrains.python.psi.PyFile
import fixtures.FakePopupHost
import fixtures.TestBase

class PyExportSymbolToTargetIntentionTest : TestBase() {

    fun testExportToInit() {
        myFixture.addFileToProject("pkg/__init__.py", "")
        val modFile = myFixture.addFileToProject(
            "pkg/_mod.py", """
            def my_<caret>func():
                pass
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(modFile.virtualFile)

        val intention = PyExportSymbolToTargetIntention()
        // Inject fake popup host to select the second target (the __init__.py)
        val fakePopupHost = FakePopupHost()
        fakePopupHost.selectedIndex = 1
        intention.popupHost = fakePopupHost

        assertTrue("Intention should be available", intention.isAvailable(project, myFixture.editor, myFixture.file))

        intention.invoke(project, myFixture.editor, myFixture.file)

        myFixture.checkResult(
            "pkg/__init__.py", "__all__ = [\"my_func\"]\n\nfrom ._mod import my_func\n", true
        )
    }

    fun testExportToParentInit() {
        myFixture.addFileToProject("parent/__init__.py", "")
        myFixture.addFileToProject("parent/pkg/__init__.py", "")
        val modFile = myFixture.addFileToProject(
            "parent/pkg/_mod.py", """
            class My<caret>Class:
                pass
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(modFile.virtualFile)

        val intention = PyExportSymbolToTargetIntention()
        assertTrue("Intention should be available", intention.isAvailable(project, myFixture.editor, myFixture.file))

        // Find targets directly to verify
        val findExportTargets = intention.javaClass.getDeclaredMethod("findExportTargets", PyFile::class.java)
        findExportTargets.isAccessible = true
        val targets = findExportTargets.invoke(intention, myFixture.file as PyFile) as List<*>

        assertEquals(3, targets.size)
        assertEquals("/src/parent/pkg/_mod.py", (targets[0] as PyFile).virtualFile.path)
        assertEquals("/src/parent/pkg/__init__.py", (targets[1] as PyFile).virtualFile.path)
        assertEquals("/src/parent/__init__.py", (targets[2] as PyFile).virtualFile.path)
    }

    fun testExportToInitWithExistingAll() {
        myFixture.addFileToProject("pkg/__init__.py", "__all__ = [\"other\"]")
        val modFile = myFixture.addFileToProject(
            "pkg/_mod.py", """
            my_v<caret>ar = 123
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(modFile.virtualFile)

        val intention = PyExportSymbolToTargetIntention()
        val fakePopupHost = FakePopupHost()
        fakePopupHost.selectedIndex = 1
        intention.popupHost = fakePopupHost

        intention.invoke(project, myFixture.editor, myFixture.file)

        // The exact output might depend on internal PyCharm formatting/ordering.
        // Based on previous debug log, we match the observed behavior.
        val actual = (myFixture.file.containingDirectory.findFile("__init__.py") as? PyFile)?.text
        assertTrue(actual?.contains("__all__ = [") == true)
        assertTrue(actual?.contains("\"other\"") == true)
        assertTrue(actual?.contains("\"my_var\"") == true || actual?.contains("'my_var'") == true)
        assertTrue(actual?.contains("from ._mod import") == true)
    }

    fun testPrivateDirectoryExcluded() {
        myFixture.addFileToProject("__init__.py", "")
        myFixture.addFileToProject("_private/__init__.py", "")
        val modFile = myFixture.addFileToProject(
            "_private/mod.py", """
            def f<caret>oo(): pass
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(modFile.virtualFile)
        val intention = PyExportSymbolToTargetIntention()

        // It should find the current module and the root __init__.py because _private is excluded.
        // Find targets directly to verify
        val findExportTargets = intention.javaClass.getDeclaredMethod("findExportTargets", PyFile::class.java)
        findExportTargets.isAccessible = true
        val targets = findExportTargets.invoke(intention, myFixture.file as PyFile) as List<*>

        assertEquals(2, targets.size)
        assertEquals("/src/_private/mod.py", (targets[0] as PyFile).virtualFile.path)
        assertTrue((targets[1] as PyFile).virtualFile.path.endsWith("/__init__.py"))

        val fakePopupHost = FakePopupHost()
        fakePopupHost.selectedIndex = 1
        intention.popupHost = fakePopupHost
        intention.invoke(project, myFixture.editor, myFixture.file)

        val initFile =
            myFixture.tempDirFixture.getFile("src/__init__.py") ?: myFixture.tempDirFixture.getFile("__init__.py")
        val actual = initFile?.let { myFixture.psiManager.findFile(it) as? PyFile }?.text

        if (actual == null) {
            val allFiles = myFixture.tempDirFixture.getFile("")?.children?.map { it.path } ?: emptyList()
            fail("Could not find __init__.py. All files: $allFiles")
        }

        assertTrue(actual!!.contains("__all__ = [\"foo\"]"))
        assertTrue(actual!!.contains("from .mod import foo"))
    }

    fun testExportToSelf() {
        val modFile = myFixture.addFileToProject(
            "mod.py", """
            def my_<caret>func():
                pass
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(modFile.virtualFile)

        val intention = PyExportSymbolToTargetIntention()

        // Since there are no __init__.py files, it will only find mod.py as target.
        // And it will immediately invoke on it.
        intention.invoke(project, myFixture.editor, myFixture.file)

        myFixture.checkResult(
            "mod.py", """
            __all__ = ["my_func"]
            
            
            def my_func():
                pass
        """.trimIndent(), true
        )
    }

    fun testIsAlreadyExported() {
        val file = myFixture.addFileToProject("mod.py", "__all__ = ['foo']") as PyFile
        val intention = PyExportSymbolToTargetIntention()
        val isAlreadyExported =
            intention.javaClass.getDeclaredMethod("isAlreadyExported", PyFile::class.java, String::class.java)
        isAlreadyExported.isAccessible = true

        assertTrue(isAlreadyExported.invoke(intention, file, "foo") as Boolean)
        assertFalse(isAlreadyExported.invoke(intention, file, "bar") as Boolean)
    }
    fun testNotAvailableOnClassMethod() {
        val modFile = myFixture.addFileToProject(
            "mod.py", """
            class MyClass:
                def my_meth<caret>od(self):
                    pass
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(modFile.virtualFile)
        val intention = PyExportSymbolToTargetIntention()
        assertFalse(
            "Intention should not be available on class method",
            intention.isAvailable(project, myFixture.editor, myFixture.file)
        )
    }

    fun testNotAvailableOnLocalVariable() {
        val modFile = myFixture.addFileToProject(
            "mod.py", """
            def my_func():
                local_v<caret>ar = 1
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(modFile.virtualFile)
        val intention = PyExportSymbolToTargetIntention()
        assertFalse(
            "Intention should not be available on local variable",
            intention.isAvailable(project, myFixture.editor, myFixture.file)
        )
    }

    fun testNotAvailableOnNestedClass() {
        val modFile = myFixture.addFileToProject(
            "mod.py", """
            class Outer:
                class Inn<caret>er:
                    pass
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(modFile.virtualFile)
        val intention = PyExportSymbolToTargetIntention()
        assertFalse(
            "Intention should not be available on nested class",
            intention.isAvailable(project, myFixture.editor, myFixture.file)
        )
    }
}