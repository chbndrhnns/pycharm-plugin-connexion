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

    fun testLambdaParameterNotTreatedAsFixtureUsageInTest() {
        myFixture.configureByText(
            "test_.py",
            """
            def test_():
                assert lambda x: x
            """.trimIndent()
        )
        myFixture.enableInspections(inspection)
        val highlights = myFixture.doHighlighting()
        val error = highlights.find { it.description?.contains("Fixture 'x' is not injected") == true }
        assertNull("Lambda parameter should not be treated as a fixture reference", error)
    }

    fun testLambdaParameterHasNoFixtureReference() {
        myFixture.configureByText(
            "test_.py",
            """
            def test_():
                assert lambda x: x
            """.trimIndent()
        )
        // Find the lambda parameter 'x'
        val file = myFixture.file as com.jetbrains.python.psi.PyFile
        val lambdaExpr = com.intellij.psi.util.PsiTreeUtil.findChildOfType(
            file,
            com.jetbrains.python.psi.PyLambdaExpression::class.java
        )
        assertNotNull("Lambda expression should exist", lambdaExpr)

        val parameter = lambdaExpr?.parameterList?.parameters?.firstOrNull()
        assertNotNull("Lambda parameter 'x' should exist", parameter)

        // Check that the parameter doesn't have a PytestFixtureReference
        val references = parameter?.references ?: emptyArray()
        val fixtureReferences = references.filterIsInstance<PytestFixtureReference>()
        assertTrue("Lambda parameter should not have a PytestFixtureReference", fixtureReferences.isEmpty())
    }
}
