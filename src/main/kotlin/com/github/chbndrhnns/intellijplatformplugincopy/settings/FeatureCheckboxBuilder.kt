package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.*

/**
 * A tag-style label with rounded rectangle background.
 * Renders text inside a colored pill/badge shape.
 */
class MaturityTagLabel(
    private val text: String,
    private val backgroundColor: Color,
    private val textColor: Color = JBColor.WHITE,
    tooltip: String? = null
) : JPanel() {

    init {
        isOpaque = false
        toolTipText = tooltip
        layout = BorderLayout()
        border = JBUI.Borders.empty(2, 6)

        add(JLabel(text).apply {
            foreground = textColor
            font = font.deriveFont(font.size2D - 1f)
        }, BorderLayout.CENTER)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val arcSize = JBUI.scale(12)
            val shape = RoundRectangle2D.Float(
                0f, 0f,
                width.toFloat(), height.toFloat(),
                arcSize.toFloat(), arcSize.toFloat()
            )

            g2.color = backgroundColor
            g2.fill(shape)
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }

    override fun getPreferredSize(): Dimension {
        val labelSize = components.firstOrNull()?.preferredSize ?: Dimension(0, 0)
        val insets = insets
        return Dimension(
            labelSize.width + insets.left + insets.right,
            labelSize.height + insets.top + insets.bottom
        )
    }

    override fun getMinimumSize(): Dimension = preferredSize
    override fun getMaximumSize(): Dimension = preferredSize
}

/**
 * Helper functions for building feature checkboxes with maturity indicators and YouTrack links.
 */
object FeatureCheckboxBuilder {

    /** Background colors for maturity tags */
    object MaturityColors {
        val INCUBATING_BG = JBColor(Color(255, 152, 0), Color(255, 152, 0)) // Orange
        val DEPRECATED_BG = JBColor(Color(244, 67, 54), Color(244, 67, 54)) // Red
        val HIDDEN_BG = JBColor(Color(158, 158, 158), Color(120, 120, 120)) // Gray
        val STABLE_BG = JBColor(Color(76, 175, 80), Color(76, 175, 80)) // Green
        val LOGGING_BG = JBColor(Color(33, 150, 243), Color(33, 150, 243)) // Blue
    }

    /**
     * Creates a maturity tag for the given maturity level.
     */
    fun createMaturityTag(maturity: FeatureMaturity, removeIn: String = ""): JComponent? {
        return when (maturity) {
            FeatureMaturity.INCUBATING -> MaturityTagLabel(
                text = "Incubating",
                backgroundColor = MaturityColors.INCUBATING_BG,
                tooltip = "This feature is experimental and may change or be removed"
            )
            FeatureMaturity.DEPRECATED -> {
                val tooltip = if (removeIn.isNotEmpty()) {
                    "This feature is deprecated and will be removed in version $removeIn"
                } else {
                    "This feature is deprecated and will be removed in a future version"
                }
                MaturityTagLabel(
                    text = "Deprecated",
                    backgroundColor = MaturityColors.DEPRECATED_BG,
                    tooltip = tooltip
                )
            }
            FeatureMaturity.HIDDEN -> MaturityTagLabel(
                text = "Hidden",
                backgroundColor = MaturityColors.HIDDEN_BG,
                tooltip = "This feature is hidden from the UI by default"
            )
            FeatureMaturity.STABLE -> null // No badge for stable features
        }
    }

    /**
     * Creates a logging badge if debug logging is currently enabled for the feature.
     *
     * @param feature The feature to check for active logging
     * @param onLoggingChanged Optional callback to trigger when logging is disabled via badge click
     * @return A badge component if logging is active, null otherwise
     */
    fun createLoggingBadge(feature: FeatureRegistry.FeatureInfo, onLoggingChanged: (() -> Unit)? = null): JComponent? {
        if (feature.loggingCategories.isEmpty()) return null

        val loggingService = FeatureLoggingService.instance()
        return if (loggingService.isLoggingEnabled(feature)) {
            MaturityTagLabel(
                text = "Logging",
                backgroundColor = MaturityColors.LOGGING_BG,
                tooltip = "Click to disable logging for this feature"
            ).apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        loggingService.disableLogging(feature)
                        // Trigger UI rebuild if callback is provided
                        onLoggingChanged?.invoke()
                    }
                })
            }
        } else {
            null
        }
    }

    /**
     * Creates a row with a feature checkbox, maturity badge, and optional YouTrack links.
     *
     * @param feature The feature info from the registry
     * @param labelOverride Optional custom label (defaults to feature.displayName)
     * @param visibleMaturities Set of maturity levels to show (if feature's maturity is not in this set, row is hidden)
     * @param onLoggingChanged Optional callback to trigger when logging state changes (for UI refresh)
     */
    fun Panel.featureRow(
        feature: FeatureRegistry.FeatureInfo,
        labelOverride: String? = null,
        visibleMaturities: Set<FeatureMaturity>? = null,
        searchTerm: String = "",
        onLoggingChanged: (() -> Unit)? = null
    ) {
        // Skip if maturity filtering is active and this feature's maturity is not visible
        if (visibleMaturities != null && feature.maturity !in visibleMaturities) {
            return
        }

        // Skip if search term is active and doesn't match
        if (searchTerm.isNotEmpty()) {
            val matchesSearch = feature.displayName.contains(searchTerm, ignoreCase = true) ||
                    feature.description.contains(searchTerm, ignoreCase = true) ||
                    (labelOverride?.contains(searchTerm, ignoreCase = true) ?: false)
            if (!matchesSearch) return
        }

        row {
            val label = labelOverride ?: feature.displayName

            checkBox(label)
                .bindSelected(feature::isEnabled, feature::setEnabled)
                .apply {
                    component.addMouseListener(object : MouseAdapter() {
                        override fun mousePressed(e: MouseEvent) {
                            handlePopup(e)
                        }

                        override fun mouseReleased(e: MouseEvent) {
                            handlePopup(e)
                        }

                        private fun handlePopup(e: MouseEvent) {
                            if (e.isPopupTrigger) {
                                val popup = JPopupMenu()
                                val copyItem = JMenuItem("Copy Feature ID")
                                copyItem.addActionListener {
                                    val selection = StringSelection(feature.id)
                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                }
                                popup.add(copyItem)

                                if (feature.loggingCategories.isNotEmpty()) {
                                    val loggingService = FeatureLoggingService.instance()
                                    val isLoggingEnabled = loggingService.isLoggingEnabled(feature)

                                    val logItemText = if (isLoggingEnabled) "Disable Debug Logs" else "Enable Debug Logs"
                                    val logItem = JMenuItem(logItemText)
                                    logItem.addActionListener {
                                        if (isLoggingEnabled) {
                                            loggingService.disableLogging(feature)
                                        } else {
                                            loggingService.enableLogging(feature)
                                        }
                                        // Trigger UI rebuild if callback is provided
                                        onLoggingChanged?.invoke()
                                    }
                                    popup.add(logItem)
                                }

                                popup.show(e.component, e.x, e.y)
                            }
                        }
                    })

                    if (feature.description.isNotEmpty()) {
                        comment(feature.description)
                    }
                }

            createMaturityTag(feature.maturity, feature.removeIn)?.let { tag ->
                cell(tag)
            }

            createLoggingBadge(feature, onLoggingChanged)?.let { badge ->
                cell(badge)
            }

            if (feature.youtrackIssues.isNotEmpty()) {
                cell(createYouTrackLinks(feature.youtrackIssues))
            }
        }
    }

    /**
     * Creates feature rows for all features in a category.
     *
     * @param category The category to filter features by
     * @param includeHidden Whether to include hidden features (default: false)
     * @param visibleMaturities Set of maturity levels to show (null means show all non-hidden)
     * @param onLoggingChanged Optional callback to trigger when logging state changes
     */
    fun Panel.featureRowsForCategory(
        category: FeatureCategory,
        includeHidden: Boolean = false,
        visibleMaturities: Set<FeatureMaturity>? = null,
        onLoggingChanged: (() -> Unit)? = null
    ) {
        val registry = FeatureRegistry.instance()
        val features = registry.getFeaturesByCategory(category)
            .filter { includeHidden || it.maturity != FeatureMaturity.HIDDEN }
            .sortedBy { it.displayName }

        features.forEach { feature ->
            featureRow(feature, visibleMaturities = visibleMaturities, onLoggingChanged = onLoggingChanged)
        }
    }

    /**
     * Creates a collapsible group for a feature category.
     *
     * @param category The category to create a group for
     * @param includeHidden Whether to include hidden features (default: false)
     * @param initiallyExpanded Whether the group should be expanded by default (default: true)
     * @param visibleMaturities Set of maturity levels to show (null means show all non-hidden)
     * @param onLoggingChanged Optional callback to trigger when logging state changes
     */
    fun Panel.featureCategoryGroup(
        category: FeatureCategory,
        includeHidden: Boolean = false,
        initiallyExpanded: Boolean = true,
        visibleMaturities: Set<FeatureMaturity>? = null,
        onLoggingChanged: (() -> Unit)? = null
    ) {
        val registry = FeatureRegistry.instance()
        val features = registry.getFeaturesByCategory(category)
            .filter { includeHidden || it.maturity != FeatureMaturity.HIDDEN }

        if (features.isEmpty()) return

        collapsibleGroup(category.displayName, initiallyExpanded) {
            featureRowsForCategory(category, includeHidden, visibleMaturities, onLoggingChanged)
        }
    }

    /**
     * Creates a panel with all visible features grouped by category.
     *
     * @param categories The categories to include (defaults to all categories)
     * @param includeHidden Whether to include hidden features (default: false)
     * @param visibleMaturities Set of maturity levels to show (null means show all non-hidden)
     * @param onLoggingChanged Optional callback to trigger when logging state changes
     */
    fun Panel.allFeaturesByCategory(
        categories: List<FeatureCategory> = FeatureCategory.entries,
        includeHidden: Boolean = false,
        visibleMaturities: Set<FeatureMaturity>? = null,
        onLoggingChanged: (() -> Unit)? = null
    ) {
        categories.forEach { category ->
            featureCategoryGroup(
                category,
                includeHidden,
                visibleMaturities = visibleMaturities,
                onLoggingChanged = onLoggingChanged
            )
        }
    }

    private fun createYouTrackLinks(issues: List<String>): ActionLink {
        return if (issues.size == 1) {
            ActionLink(issues[0]) {
                BrowserUtil.browse(FeatureRegistry.FeatureInfo.getYouTrackUrl(issues[0]))
            }.apply {
                toolTipText = "Open ${issues[0]} in YouTrack"
            }
        } else {
            ActionLink("${issues.size} issues") {
                // Open the first issue, or could show a popup with all issues
                BrowserUtil.browse(FeatureRegistry.FeatureInfo.getYouTrackUrl(issues[0]))
            }.apply {
                toolTipText = "Related issues: ${issues.joinToString(", ")}"
            }
        }
    }
}
