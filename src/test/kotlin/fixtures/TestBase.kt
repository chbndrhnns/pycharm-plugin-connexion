package fixtures

import com.jetbrains.python.inspections.PyTypeCheckerInspection

abstract class TestBase : MyPlatformTestCase() {
    override fun setUp() {
        PythonTestSetup.configurePythonHelpers()
        super.setUp()
        PythonTestSetup.createAndRegisterSdk(
            root = myFixture.tempDirFixture.getFile("/")!!,
            disposable = testRootDisposable
        )
        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
    }
}
