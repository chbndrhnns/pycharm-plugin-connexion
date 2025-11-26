package com.github.chbndrhnns.intellijplatformplugincopy

import PythonMockSdk
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.psi.LanguageLevel
import java.nio.file.Paths

abstract class TestBase : MyPlatformTestCase() {
    override fun setUp() {
        System.setProperty(
            "idea.python.helpers.path",
            Paths.get(PathManager.getHomePath(), "plugins", "python-ce", "helpers").toString()
        );
        super.setUp()
        // Reset plugin settings to a known baseline after the IntelliJ test application is initialized
        PluginSettingsState.instance().loadState(PluginSettingsState.State())
        val sdk = PythonMockSdk.create(LanguageLevel.PYTHON311, myFixture.tempDirFixture.getFile("/")!!)
        Disposer.register(testRootDisposable) {
            runWriteAction {
                ProjectJdkTable.getInstance().removeJdk(sdk)
            }
        }
        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
    }
}