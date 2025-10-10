package fixtures

import PythonMockSdk
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.psi.LanguageLevel
import java.nio.file.Paths

/**
 * Utility object for shared Python SDK setup logic in tests.
 * Consolidates the duplicate setup code from TestBase and HeavyTestBase.
 */
object PythonTestSetup {

    /**
     * Configures the Python helpers path system property.
     * Should be called early in test setup, before the SDK is created.
     */
    fun configurePythonHelpers() {
        System.setProperty(
            "idea.python.helpers.path",
            Paths.get(PathManager.getHomePath(), "plugins", "python-ce", "helpers").toString()
        )
    }

    /**
     * Creates a Python mock SDK and registers it for cleanup when the disposable is disposed.
     *
     * @param root The virtual file root for the SDK
     * @param disposable The disposable to register cleanup with
     * @param languageLevel The Python language level (defaults to Python 3.11)
     * @param addToJdkTable Whether to add the SDK to the global JDK table (needed for heavy tests)
     * @return The created SDK
     */
    fun createAndRegisterSdk(
        root: VirtualFile,
        disposable: Disposable,
        languageLevel: LanguageLevel = LanguageLevel.PYTHON311,
        addToJdkTable: Boolean = false
    ): Sdk {
        val sdk = PythonMockSdk.create(languageLevel, root)

        if (addToJdkTable) {
            runWriteAction {
                ProjectJdkTable.getInstance().addJdk(sdk)
            }
        }

        Disposer.register(disposable) {
            runWriteAction {
                ProjectJdkTable.getInstance().removeJdk(sdk)
            }
        }

        return sdk
    }
}
