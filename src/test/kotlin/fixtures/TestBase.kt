package fixtures

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.jetbrains.python.inspections.PyTypeCheckerInspection

abstract class TestBase : MyPlatformTestCase() {
    override fun setUp() {
        PythonTestSetup.configurePythonHelpers()
        super.setUp()
        // Reset plugin settings to a known baseline after the IntelliJ test application is initialized
        val svc = PluginSettingsState.instance()
        svc.loadState(PluginSettingsState.State())
        PythonTestSetup.createAndRegisterSdk(
            root = myFixture.tempDirFixture.getFile("/")!!,
            disposable = testRootDisposable
        )
        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
    }
}