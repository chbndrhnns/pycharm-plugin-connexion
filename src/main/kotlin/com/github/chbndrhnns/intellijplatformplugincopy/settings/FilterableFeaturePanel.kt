package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * A wrapper panel that combines a [MaturityFilterPanel] with a dynamically filterable feature list.
 * When filter tags are toggled, the feature list is rebuilt to show only matching features.
 */
class FilterableFeaturePanel(
    private val showHidden: Boolean = false,
    private val contentBuilder: Panel.(visibleMaturities: Set<FeatureMaturity>) -> Unit
) : JPanel(BorderLayout()) {

    private val contentPanel = JPanel(BorderLayout())
    private var currentMaturities: Set<FeatureMaturity> = if (showHidden) {
        FeatureMaturity.entries.toSet()
    } else {
        setOf(FeatureMaturity.STABLE, FeatureMaturity.INCUBATING, FeatureMaturity.DEPRECATED)
    }

    private val filterPanel = MaturityFilterPanel(
        onFilterChanged = { maturities ->
            currentMaturities = maturities
            rebuildContent()
        },
        showHidden = showHidden,
        initialSelection = currentMaturities
    )

    init {
        border = JBUI.Borders.empty()

        // Add filter panel at the top
        add(filterPanel, BorderLayout.NORTH)

        // Add scrollable content area
        val scrollPane = JBScrollPane(contentPanel).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        add(scrollPane, BorderLayout.CENTER)

        // Build initial content
        rebuildContent()
    }

    private fun rebuildContent() {
        contentPanel.removeAll()

        val newPanel = panel {
            contentBuilder(currentMaturities)
        }

        contentPanel.add(newPanel, BorderLayout.NORTH)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    /**
     * Returns the currently selected maturity filter.
     */
    fun getSelectedMaturities(): Set<FeatureMaturity> = currentMaturities

    /**
     * Creates a DialogPanel wrapper for use with BoundConfigurable.
     * Note: This returns a DialogPanel but the actual content is managed by this FilterableFeaturePanel.
     */
    fun asDialogPanel(): DialogPanel {
        return panel {
            row {
                cell(this@FilterableFeaturePanel)
                    .resizableColumn()
                    .align(com.intellij.ui.dsl.builder.Align.FILL)
            }.resizableRow()
        }
    }
}

/**
 * Helper function to create a filterable feature panel with the standard layout.
 */
fun createFilterableFeaturePanel(
    showHidden: Boolean = false,
    contentBuilder: Panel.(visibleMaturities: Set<FeatureMaturity>) -> Unit
): FilterableFeaturePanel {
    return FilterableFeaturePanel(showHidden, contentBuilder)
}
