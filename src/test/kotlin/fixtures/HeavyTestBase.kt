package fixtures

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.jetbrains.python.inspections.PyTypeCheckerInspection

/**
 * Heavy variant of [TestBase] using a real project fixture created via
 * [IdeaTestFixtureFactory]. This follows the patterns outlined in
 * `docs/heavy-fixture.md` / `docs/heavy-test.md`:
 *
 * - A real project is created and opened through the normal project-opening
 *   pipeline (via [IdeaProjectTestFixture]).
 * - [myFixture] is a regular [CodeInsightTestFixture] backed by that heavy
 *   project, so cross-file resolution behaves like in the IDE.
 * - Python-specific setup mirrors what [TestBase] does: helpers path, mock SDK
 *   and type checker inspection.
 */
abstract class HeavyTestBase : UsefulTestCase() {

    protected lateinit var myFixture: CodeInsightTestFixture
        private set

    private lateinit var projectFixture: IdeaProjectTestFixture

    override fun setUp() {
        super.setUp()

        val fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory()

        // Use the default heavy project fixture builder; in this setup the
        // underlying implementation already creates a module, so
        // myFixture.module will be non-null once the CodeInsight fixture is
        // created.
        @Suppress("UNCHECKED_CAST")
        val fixtureBuilder = fixtureFactory.createFixtureBuilder(name)
        projectFixture = fixtureBuilder.fixture

        myFixture = fixtureFactory.createCodeInsightFixture(projectFixture)
        myFixture.testDataPath = "src/test/testData"

        myFixture.setUp()

        // At this point a real project is created and opened. Configure a
        // Python SDK and ensure there is a module with proper content/source
        // roots so that heavy tests see the same indexing behaviour as the
        // IDE.
        setUpPython()
    }

    private fun setUpPython() {
        PythonTestSetup.configurePythonHelpers()

        val root = myFixture.tempDirFixture.getFile("/")!!

        // Register SDK in the global JDK table so the project sees it.
        // Let tests decide which directories become content/source
        // roots (see docs/heavy2.md). Here we only prepare the SDK and
        // attach it to the project-wide JDK table.
        PythonTestSetup.createAndRegisterSdk(
            root = root,
            disposable = myFixture.testRootDisposable,
            addToJdkTable = true
        )

        // Enable the Python type checker so heavy tests can assert on
        // inspections that rely on indices and the configured SDK.
        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
    }

    /**
     * Make the temp directory used by [myFixture] a content and source root of
     * the single module provided by the heavy project fixture. This follows the
     * pattern described in `docs/heavy2.md` and is useful for cross-file
     * indexing and resolution tests.
     */
    protected fun configureTempDirAsContentAndSourceRoot() {
        val root = myFixture.tempDirFixture.getFile("/") ?: return
        val project = myFixture.project

        runWriteAction {
            val module: Module = myFixture.module
                ?: ModuleManager.getInstance(project).newModule(root.path + "/heavy_test.iml", "JAVA_MODULE")
            PsiTestUtil.addContentRoot(module, root)
            PsiTestUtil.addSourceRoot(module, root)
        }
    }

    /**
     * Waits for indices (smart mode) and then runs highlighting in the current
     * file. Heavy tests can use this to ensure that all indices are ready
     * before performing PSI-based assertions.
     */
    protected fun waitForSmartModeAndHighlight() {
        DumbService.getInstance(myFixture.project).waitForSmartMode()
        myFixture.doHighlighting()
    }

    override fun tearDown() {
        try {
            if (this::myFixture.isInitialized) {
                myFixture.tearDown()
            }
        } finally {
            super.tearDown()
        }
    }
}