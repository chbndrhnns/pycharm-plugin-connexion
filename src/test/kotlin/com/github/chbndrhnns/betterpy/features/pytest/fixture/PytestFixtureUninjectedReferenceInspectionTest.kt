package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import fixtures.TestBase
import fixtures.doMultiFileInspectionTest

class PytestFixtureUninjectedReferenceInspectionTest : TestBase() {

    private val inspection = PytestFixtureUninjectedReferenceInspection::class.java
    private val fixName = "BetterPy: Inject pytest fixture 'myf'"
    private val path = "inspections/PytestFixtureUninjectedReferenceInspection/InjectFixture"

    fun testInjectFixtureParameterQuickFix() {
        myFixture.doMultiFileInspectionTest(
            files = listOf("$path/conftest.py", "$path/test.py"),
            inspection = inspection,
            targetFile = "$path/test.py",
            fixFamilyName = fixName,
            resultFileToCheck = "$path/test.py",
            expectedResultFile = "$path/test_after.py",
            checkHighlighting = false
        )
    }

    fun testHighlightsUninjectedFixtureReference() {
        myFixture.configureByFiles("$path/conftest.py", "$path/test.py")
        val testFile = myFixture.findFileInTempDir("$path/test.py")
        myFixture.openFileInEditor(testFile!!)

        myFixture.enableInspections(inspection)
        val highlights = myFixture.doHighlighting()

        val error = highlights.find { it.description?.contains("Fixture 'myf' is not injected") == true }
        assertNotNull("Expected uninjected fixture highlight", error)
    }

    fun testNoHighlightWhenSettingDisabled() {
        myFixture.configureByFiles("$path/conftest.py", "$path/test.py")
        val testFile = myFixture.findFileInTempDir("$path/test.py")
        myFixture.openFileInEditor(testFile!!)

        PluginSettingsState.instance().state.enablePytestFixtureUninjectedReferenceInspection = false
        try {
            myFixture.enableInspections(inspection)
            val highlights = myFixture.doHighlighting()
            val error = highlights.find { it.description?.contains("Fixture 'myf' is not injected") == true }
            assertNull("Highlight should NOT be present when setting is disabled", error)
        } finally {
            PluginSettingsState.instance().state.enablePytestFixtureUninjectedReferenceInspection = true
        }
    }
}
