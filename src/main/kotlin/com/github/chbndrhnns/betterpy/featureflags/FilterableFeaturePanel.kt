package com.github.chbndrhnns.betterpy.featureflags

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel

/**
 * Metadata for a feature row to enable filtering without rebuilding the panel.
 */
data class RowMetadata(
    val row: Row,
    val maturity: FeatureMaturity,
    val searchableText: String,
    val isEnabled: () -> Boolean
)

/**
 * A DialogPanel that combines a [MaturityFilterPanel] with a dynamically filterable feature list.
 * When filter tags are toggled, row visibility is updated without rebuilding the panel.
 */
class FilterableFeaturePanel(
    private val showHidden: Boolean = false,
    private val contentBuilder: Panel.(onFilterRefreshRequested: () -> Unit) -> List<RowMetadata>
) {

    private var currentMaturities: Set<FeatureMaturity> = if (showHidden) {
        FeatureMaturity.entries.toSet()
    } else {
        setOf(FeatureMaturity.STABLE, FeatureMaturity.INCUBATING, FeatureMaturity.DEPRECATED)
    }
    private var currentEnabledFilter: EnabledFilter = EnabledFilter.ALL
    private var currentSearchTerm: String = ""
    private var rowMetadata: List<RowMetadata> = emptyList()

    private val enabledFilterPanel = EnabledFilterPanel(
        initialSelection = currentEnabledFilter,
        onFilterChanged = { filter ->
            currentEnabledFilter = filter
            applyFilters()
        }
    )

    private val filterPanel = MaturityFilterPanel(
        onFilterChanged = { maturities ->
            currentMaturities = maturities
            applyFilters()
        },
        showHidden = showHidden,
        initialSelection = currentMaturities
    )

    private val searchField = com.intellij.ui.SearchTextField().apply {
        addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                currentSearchTerm = text.trim()
                applyFilters()
            }
        })
    }

    private val dialogPanel: DialogPanel = panel {
        row {
            cell(enabledFilterPanel)
        }.bottomGap(com.intellij.ui.dsl.builder.BottomGap.SMALL)

        row {
            cell(filterPanel)
        }.bottomGap(com.intellij.ui.dsl.builder.BottomGap.SMALL)

        row {
            cell(searchField)
                .resizableColumn()
                .align(com.intellij.ui.dsl.builder.Align.FILL)
        }.bottomGap(com.intellij.ui.dsl.builder.BottomGap.SMALL)

        rowMetadata = contentBuilder(::requestFilterRefresh)
    }

    init {
        // Apply initial filter
        applyFilters()
    }

    private fun applyFilters() {
        rowMetadata.forEach { metadata ->
            val matchesEnabled = when (currentEnabledFilter) {
                EnabledFilter.ALL -> true
                EnabledFilter.ENABLED -> metadata.isEnabled()
                EnabledFilter.DISABLED -> !metadata.isEnabled()
            }

            if (!matchesEnabled) {
                metadata.row.visible(false)
                return@forEach
            }

            val matchesMaturity = metadata.maturity in currentMaturities
            val matchesSearch = currentSearchTerm.isEmpty() ||
                    metadata.searchableText.contains(currentSearchTerm, ignoreCase = true)

            metadata.row.visible(matchesMaturity && matchesSearch)
        }

        dialogPanel.revalidate()
        dialogPanel.repaint()
    }

    private fun requestFilterRefresh() {
        applyFilters()
    }

    /**
     * Returns the currently selected maturity filter.
     */
    fun getSelectedMaturities(): Set<FeatureMaturity> = currentMaturities

    /**
     * Returns the DialogPanel that contains all form components including filters.
     * This is needed for BoundConfigurable to properly track modifications.
     */
    fun asDialogPanel(): DialogPanel {
        return dialogPanel
    }
}

/**
 * Helper function to create a filterable feature panel with the standard layout.
 */
fun createFilterableFeaturePanel(
    showHidden: Boolean = false,
    contentBuilder: Panel.(onFilterRefreshRequested: () -> Unit) -> List<RowMetadata>
): FilterableFeaturePanel {
    return FilterableFeaturePanel(showHidden, contentBuilder)
}
