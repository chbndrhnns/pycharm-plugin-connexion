package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.ui.DialogPanel
import fixtures.TestBase
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.text.JTextComponent

class PluginSettingsSnapshotTest : TestBase() {

    fun testPluginSettingsUiSnapshot() {
        val configurable = PluginSettingsConfigurable()
        val panel = configurable.createComponent() as DialogPanel
        assertNotNull(panel)
        configurable.reset() // Load settings into UI

        val registry = FeatureRegistry.instance()
        val featureId = "jump-to-pytest-node-in-test-tree"

        // 1. Initial snapshot
        val initialSnapshot = buildSnapshot(panel)

        // 2. Change state in registry (persistent state)
        val originalValue = registry.isFeatureEnabled(featureId)
        try {
            registry.setFeatureEnabled(featureId, !originalValue)

            // 3. Second snapshot - should be IDENTICAL because panel is bound to snapshot, not registry
            val secondSnapshot = buildSnapshot(panel)
            assertEquals(
                "UI should not change when registry changes because it is bound to a snapshot baseline",
                initialSnapshot,
                secondSnapshot
            )
        } finally {
            registry.setFeatureEnabled(featureId, originalValue)
        }

        val expectedSnapshotUrl = this.javaClass.getResource("/settings_snapshot.txt")
        assertNotNull("Snapshot file not found", expectedSnapshotUrl)

        val expectedSnapshot = expectedSnapshotUrl!!.readText().replace("\r\n", "\n").trim()
        val actualSnapshot = initialSnapshot.replace("\r\n", "\n").trim()

        // Uncomment to update snapshot
//        java.io.File("src/test/resources/settings_snapshot.txt").writeText(actualSnapshot)

        assertEquals(expectedSnapshot, actualSnapshot)
    }

    private fun buildSnapshot(component: Component, indent: String = ""): String {
        if (!component.isVisible) return ""

        val sb = StringBuilder()
        sb.append(indent)

        when (component) {
            is JLabel -> sb.append("Label: '${component.text}'")
            is JTextComponent -> sb.append("TextField: '${component.text}'")
            is javax.swing.JCheckBox -> {
                val state = if (component.isSelected) "[x]" else "[ ]"
                sb.append("CheckBox: $state '${component.text}'")
            }

            is javax.swing.AbstractButton -> sb.append("Button: '${component.text}'")
            else -> sb.append(component.javaClass.simpleName)
        }
        sb.append("\n")

        if (component is Container) {
            for (child in component.components) {
                sb.append(buildSnapshot(child, "$indent  "))
            }
        }
        return sb.toString()
    }
}