package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import PythonMockSdk
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.psi.LanguageLevel
import java.nio.file.Paths

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

        @Suppress("UNCHECKED_CAST")
        val fixtureBuilder = fixtureFactory.createFixtureBuilder(name)
        projectFixture = fixtureBuilder.fixture

        myFixture = fixtureFactory.createCodeInsightFixture(projectFixture)
        myFixture.testDataPath = "src/test/testData"

        myFixture.setUp()

        setUpPython()
    }

    private fun setUpPython() {
        System.setProperty(
            "idea.python.helpers.path",
            Paths.get(PathManager.getHomePath(), "plugins", "python-ce", "helpers").toString(),
        )

        val sdk = PythonMockSdk.create(LanguageLevel.PYTHON311, myFixture.tempDirFixture.getFile("/")!!)

        Disposer.register(myFixture.testRootDisposable) {
            runWriteAction {
                ProjectJdkTable.getInstance().removeJdk(sdk)
            }
        }

        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
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
