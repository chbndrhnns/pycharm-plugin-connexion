package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.MyPlatformTestCase
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.psi.LanguageLevel
import jetbrains.python.fixtures.PythonMockSdk

abstract class TestBase : MyPlatformTestCase() {
    override fun setUp() {
        super.setUp()
        val sdk = PythonMockSdk.create(LanguageLevel.PYTHON311, myFixture.tempDirFixture.getFile("/")!!)
        Disposer.register(testRootDisposable) {
            com.intellij.openapi.application.runWriteAction {
                ProjectJdkTable.getInstance().removeJdk(sdk)
            }
        }
        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
    }
}