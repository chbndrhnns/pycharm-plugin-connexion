package com.github.chbndrhnns.betterpy.features.actions

import com.intellij.openapi.actionSystem.ActionManager
import fixtures.TestBase

class CopyDiagnosticDataActionTest : TestBase() {

    fun testActionIsRegistered() {
        val actionManager = ActionManager.getInstance()
        val action =
            actionManager.getAction("com.github.chbndrhnns.betterpy.features.actions.CopyDiagnosticDataAction")
        assertNotNull("Action should be registered in plugin.xml", action)
        assertTrue(
            "Registered action should be CopyDiagnosticDataAction",
            action is CopyDiagnosticDataAction
        )
    }

    fun testDiagnosticDataFormatting() {
        val pluginVersion = "1.2.3"
        val ideBuildNumber = "PY-243.12345"

        val result = CopyDiagnosticDataAction.formatDiagnosticData(pluginVersion, ideBuildNumber, emptyList())

        val expected = """
            BetterPy Plugin Version: 1.2.3
            IDE Build Number: PY-243.12345
            Enabled Features: none
        """.trimIndent()

        assertEquals(expected, result)
    }

    fun testGetIdeBuildNumberReturnsNonEmpty() {
        val buildNumber = CopyDiagnosticDataAction.getIdeBuildNumber()
        assertNotNull("IDE build number should not be null", buildNumber)
        assertTrue("IDE build number should not be empty", buildNumber.isNotEmpty())
    }

    fun testGetPluginVersionReturnsValue() {
        val version = CopyDiagnosticDataAction.getPluginVersion()
        assertNotNull("Plugin version should not be null", version)
        // In test environment, plugin may not be loaded, so version could be "Unknown"
        assertTrue("Plugin version should not be empty", version.isNotEmpty())
    }

    fun testGetDiagnosticDataContainsExpectedLabels() {
        val diagnosticData = CopyDiagnosticDataAction.getDiagnosticData()
        assertTrue(
            "Diagnostic data should contain plugin version label",
            diagnosticData.contains("BetterPy Plugin Version:")
        )
        assertTrue(
            "Diagnostic data should contain IDE build number label",
            diagnosticData.contains("IDE Build Number:")
        )
        assertTrue(
            "Diagnostic data should contain enabled features label",
            diagnosticData.contains("Enabled Features:")
        )
    }
}
