package fixtures

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.LanguageLevel
import jetbrains.python.fixtures.PyLightProjectDescriptor

abstract class MyPlatformTestCase : BasePlatformTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return PyLightProjectDescriptor(LanguageLevel.PYTHON311)
    }

    override fun getTestDataPath(): String = "src/test/testData"

    fun runWithSourceRoots(sourceRoots: List<VirtualFile>, runnable: Runnable) {
        sourceRoots.forEach { root -> PsiTestUtil.addSourceRoot(module, root) }
        try {
            runnable.run()
        } finally {
            sourceRoots.forEach { root -> PsiTestUtil.removeSourceRoot(module, root) }
        }
    }

    fun runWithTestSourceRoots(testSourceRoots: List<VirtualFile>, runnable: Runnable) {
        testSourceRoots.forEach { root -> PsiTestUtil.addSourceRoot(module, root, true) }
        try {
            runnable.run()
        } finally {
            testSourceRoots.forEach { root -> PsiTestUtil.removeSourceRoot(module, root) }
        }
    }

}