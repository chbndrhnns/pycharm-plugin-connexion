package fixtures

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.LanguageLevel
import jetbrains.python.fixtures.PyLightProjectDescriptor

abstract class MyPlatformTestCase : BasePlatformTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return PyLightProjectDescriptor(LanguageLevel.PYTHON311)
    }

    override fun getTestDataPath(): String = "src/test/testData"
}