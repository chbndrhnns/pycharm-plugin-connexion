package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import fixtures.TestBase

class PytestFixtureCreateQuickFixTest : TestBase() {

    override fun setUp() {
        super.setUp()
        PluginSettingsState.instance().state.enableAdvancedPytestFixtureFeatures = true
        myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    }

    fun testCreateFixtureFromUnresolvedParameter() {
        myFixture.configureByText(
            "test_fix.py", """
            import pytest
            
            @pytest.fixture
            def dep(missing_fixture<caret>):
                pass
            """.trimIndent()
        )

        val intention = CreateFixtureFromParameterIntention()
        val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
            ?: throw AssertionError("Expected PSI element at caret.")
        assertTrue(
            "Create fixture intention should be available.",
            intention.isAvailable(project, myFixture.editor, element)
        )
        myFixture.launchAction(intention)

        val expected = """
            import pytest
            

            @pytest.fixture
            def missing_fixture():
                pass

            
            @pytest.fixture
            def dep(missing_fixture):
                pass
            """.trimIndent()
        val actual = myFixture.file.text
        assertEquals("Unexpected content:\n$actual", expected, actual)
    }
}
