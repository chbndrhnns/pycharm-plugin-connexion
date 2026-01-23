package com.github.chbndrhnns.intellijplatformplugincopy.featureflags

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import fixtures.TestBase
import io.github.classgraph.ClassGraph
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.text.JTextComponent

class PluginSettingsSnapshotTest : TestBase() {

    /**
     * Automatically discovers all BoundConfigurable subclasses in the settings package
     * and returns those that are not the main PluginSettingsConfigurable (i.e., sub-menus).
     */
    private fun discoverSubConfigurables(): List<Configurable> {
        return ClassGraph()
            .acceptPackages("com.github.chbndrhnns.intellijplatformplugincopy.featureflags")
            .enableClassInfo()
            .scan()
            .use { scanResult ->
                scanResult.getSubclasses(BoundConfigurable::class.java)
                    .mapNotNull { classInfo ->
                        try {
                            val clazz = classInfo.loadClass()
                            // Exclude the main configurable, only get sub-menus
                            if (clazz != PluginSettingsConfigurable::class.java) {
                                clazz.getDeclaredConstructor().newInstance() as? Configurable
                            } else null
                        } catch (e: Exception) {
                            // Skip classes that can't be instantiated
                            null
                        }
                    }
            }
    }

    private val subConfigurables: List<Configurable> by lazy {
        discoverSubConfigurables()
    }

    fun testPluginSettingsUiSnapshot() {
        val mainConfigurable = PluginSettingsConfigurable()
        val mainPanel = mainConfigurable.createComponent() as DialogPanel
        assertNotNull(mainPanel)
        mainConfigurable.reset() // Load settings into UI

        // Create and initialize all sub-configurable panels
        val childPanels = subConfigurables.map { child ->
            val panel = child.createComponent() as DialogPanel
            child.reset()
            child.displayName to panel
        }

        // 1. Initial snapshot
        val initialSnapshot = buildCompleteSnapshot(mainConfigurable.displayName, mainPanel, childPanels)

        val expectedSnapshotUrl = this.javaClass.getResource("/settings_snapshot.txt")
        assertNotNull("Snapshot file not found", expectedSnapshotUrl)

        val expectedSnapshot = expectedSnapshotUrl!!.readText().replace("\r\n", "\n").trim()
        val actualSnapshot = initialSnapshot.replace("\r\n", "\n").trim()

        // Uncomment to update snapshot
//        java.io.File("src/test/resources/settings_snapshot.txt").writeText(actualSnapshot)

        assertEquals(expectedSnapshot, actualSnapshot)
    }

    fun testPluginSettingsUiDoesNotChangeAfterRegistryUpdate() {
        val mainConfigurable = PluginSettingsConfigurable()
        val mainPanel = mainConfigurable.createComponent() as DialogPanel
        assertNotNull(mainPanel)
        mainConfigurable.reset() // Load settings into UI

        val childPanels = subConfigurables.map { child ->
            val panel = child.createComponent() as DialogPanel
            child.reset()
            child.displayName to panel
        }

        val initialSnapshot = buildCompleteSnapshot(mainConfigurable.displayName, mainPanel, childPanels)

        val registry = FeatureRegistry.instance()
        val featureId = "jump-to-pytest-node-in-test-tree"
        val originalValue = registry.isFeatureEnabled(featureId)
        try {
            registry.setFeatureEnabled(featureId, !originalValue)

            val secondSnapshot = buildCompleteSnapshot(mainConfigurable.displayName, mainPanel, childPanels)
            assertEquals(
                "UI should not change when registry changes because it is bound to a snapshot baseline",
                initialSnapshot,
                secondSnapshot
            )
        } finally {
            registry.setFeatureEnabled(featureId, originalValue)
        }
    }

    private fun buildCompleteSnapshot(
        mainDisplayName: String,
        mainPanel: DialogPanel,
        childPanels: List<Pair<String, DialogPanel>>
    ): String {
        val sb = StringBuilder()
        sb.append("=== $mainDisplayName ===\n")
        sb.append(buildSnapshot(mainPanel).trimEnd())

        childPanels.forEach { (childName, childPanel) ->
            sb.append("\n\n")
            sb.append("=== $childName ===\n")
            sb.append(buildSnapshot(childPanel).trimEnd())
        }

        return sb.toString()
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
