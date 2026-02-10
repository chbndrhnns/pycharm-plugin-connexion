package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.ui.ScopePinManager
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScopePinManagerTest {

    private lateinit var testFile: VirtualFile
    private lateinit var conftestFile: VirtualFile
    private lateinit var otherTestFile: VirtualFile
    private lateinit var nonTestFile: VirtualFile

    private val filesWithTests = mutableSetOf<VirtualFile>()
    private lateinit var manager: ScopePinManager

    @Before
    fun setUp() {
        testFile = FakeVirtualFile("/project/test_foo.py")
        conftestFile = FakeVirtualFile("/project/conftest.py")
        otherTestFile = FakeVirtualFile("/project/test_bar.py")
        nonTestFile = FakeVirtualFile("/project/utils.py")

        filesWithTests.clear()
        filesWithTests.add(testFile)
        filesWithTests.add(otherTestFile)

        manager = ScopePinManager { file -> file in filesWithTests }
    }

    @Test
    fun `initially not pinned`() {
        assertNull(manager.pinnedFile)
        assertNull(manager.scopeFile())
    }

    @Test
    fun `fixture navigation to same file does not pin`() {
        manager.onFixtureNavigated(testFile, testFile, scopeToCurrentFile = true, followCaret = true)
        assertNull(manager.pinnedFile)
    }

    @Test
    fun `fixture navigation to different file pins current file`() {
        manager.onFixtureNavigated(testFile, conftestFile, scopeToCurrentFile = true, followCaret = true)
        assertEquals(testFile, manager.pinnedFile)
        assertEquals(testFile, manager.scopeFile())
    }

    @Test
    fun `fixture navigation does not pin when scope disabled`() {
        manager.onFixtureNavigated(testFile, conftestFile, scopeToCurrentFile = false, followCaret = true)
        assertNull(manager.pinnedFile)
    }

    @Test
    fun `fixture navigation does not pin when follow disabled`() {
        manager.onFixtureNavigated(testFile, conftestFile, scopeToCurrentFile = true, followCaret = false)
        assertNull(manager.pinnedFile)
    }

    @Test
    fun `editor change to non-test file keeps pin and suppresses update`() {
        manager.onFixtureNavigated(testFile, conftestFile, scopeToCurrentFile = true, followCaret = true)
        val shouldUpdate = manager.onEditorChanged(nonTestFile)
        assertFalse(shouldUpdate)
        assertEquals(testFile, manager.pinnedFile)
    }

    @Test
    fun `editor change to file with tests clears pin and allows update`() {
        manager.onFixtureNavigated(testFile, conftestFile, scopeToCurrentFile = true, followCaret = true)
        val shouldUpdate = manager.onEditorChanged(otherTestFile)
        assertTrue(shouldUpdate)
        assertNull(manager.pinnedFile)
    }

    @Test
    fun `editor change to original test file clears pin`() {
        manager.onFixtureNavigated(testFile, conftestFile, scopeToCurrentFile = true, followCaret = true)
        val shouldUpdate = manager.onEditorChanged(testFile)
        assertTrue(shouldUpdate)
        assertNull(manager.pinnedFile)
    }

    @Test
    fun `editor change when not pinned always allows update`() {
        val shouldUpdate = manager.onEditorChanged(nonTestFile)
        assertTrue(shouldUpdate)
    }

    @Test
    fun `editor change to conftest keeps pin when conftest has no tests`() {
        manager.onFixtureNavigated(testFile, conftestFile, scopeToCurrentFile = true, followCaret = true)
        val shouldUpdate = manager.onEditorChanged(conftestFile)
        assertFalse(shouldUpdate)
        assertEquals(testFile, manager.pinnedFile)
    }

    @Test
    fun `reset clears pin`() {
        manager.onFixtureNavigated(testFile, conftestFile, scopeToCurrentFile = true, followCaret = true)
        manager.reset()
        assertNull(manager.pinnedFile)
    }

    /**
     * Minimal [VirtualFile] fake for unit testing. Only [getPath] is meaningful.
     */
    private class FakeVirtualFile(private val fakePath: String) : com.intellij.testFramework.LightVirtualFile() {
        override fun getPath(): String = fakePath
        override fun getName(): String = fakePath.substringAfterLast('/')
    }
}
