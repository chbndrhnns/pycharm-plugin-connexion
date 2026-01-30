package com.github.chbndrhnns.betterpy.featureflags

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * A clickable tag-style toggle button for maturity filtering.
 * When selected, it has a filled background; when deselected, it has an outline style.
 */
class MaturityFilterTag(
    private val maturity: FeatureMaturity,
    private val label: String,
    private val activeColor: Color,
    initiallySelected: Boolean = true,
    private val onToggle: (FeatureMaturity, Boolean) -> Unit
) : JPanel() {

    var isSelected: Boolean = initiallySelected
        private set

    private val textLabel: JLabel

    init {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        layout = BorderLayout()
        border = JBUI.Borders.empty(4, 10)

        textLabel = JLabel(label).apply {
            font = font.deriveFont(font.size2D - 1f)
            horizontalAlignment = JLabel.CENTER
        }
        add(textLabel, BorderLayout.CENTER)

        updateAppearance()

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                toggle()
            }

            override fun mouseEntered(e: MouseEvent) {
                repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                repaint()
            }
        })
    }

    fun toggle() {
        isSelected = !isSelected
        updateAppearance()
        onToggle(maturity, isSelected)
    }

    fun setSelected(selected: Boolean) {
        if (isSelected != selected) {
            isSelected = selected
            updateAppearance()
        }
    }

    private fun updateAppearance() {
        textLabel.foreground = if (isSelected) JBColor.WHITE else activeColor
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val arcSize = JBUI.scale(14)
            val shape = RoundRectangle2D.Float(
                1f, 1f,
                (width - 2).toFloat(), (height - 2).toFloat(),
                arcSize.toFloat(), arcSize.toFloat()
            )

            if (isSelected) {
                // Filled background when selected
                g2.color = activeColor
                g2.fill(shape)
            } else {
                // Outline only when deselected
                g2.color = activeColor
                g2.stroke = BasicStroke(JBUI.scale(2).toFloat())
                g2.draw(shape)
            }

            // Hover effect
            if (getMousePosition() != null) {
                g2.color = Color(255, 255, 255, 30)
                g2.fill(shape)
            }
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }

    override fun getPreferredSize(): Dimension {
        val labelSize = textLabel.preferredSize
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
 * A panel containing clickable maturity filter tags.
 * Allows users to filter the feature list by maturity level.
 */
class MaturityFilterPanel(
    private val onFilterChanged: (Set<FeatureMaturity>) -> Unit,
    showHidden: Boolean = false,
    initialSelection: Set<FeatureMaturity> = if (showHidden) {
        FeatureMaturity.entries.toSet()
    } else {
        setOf(FeatureMaturity.STABLE, FeatureMaturity.INCUBATING, FeatureMaturity.DEPRECATED)
    }
) : JPanel() {

    private val filterTags = mutableMapOf<FeatureMaturity, MaturityFilterTag>()
    private var selectedMaturity: FeatureMaturity = initialSelection.firstOrNull()
        ?: if (showHidden) FeatureMaturity.HIDDEN else FeatureMaturity.STABLE

    init {
        layout = FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4))
        isOpaque = false
        border = JBUI.Borders.empty(4, 0, 8, 0)

        // Add label
        add(JLabel("Filter by maturity:").apply {
            foreground = JBColor.GRAY
        })

        // Create filter tags for each maturity level
        createFilterTag(
            FeatureMaturity.STABLE,
            "Stable",
            FeatureCheckboxBuilder.MaturityColors.STABLE_BG
        )
        createFilterTag(
            FeatureMaturity.INCUBATING,
            "Incubating",
            FeatureCheckboxBuilder.MaturityColors.INCUBATING_BG
        )
        createFilterTag(
            FeatureMaturity.DEPRECATED,
            "Deprecated",
            FeatureCheckboxBuilder.MaturityColors.DEPRECATED_BG
        )
        if (showHidden) {
            createFilterTag(
                FeatureMaturity.HIDDEN,
                "Hidden",
                FeatureCheckboxBuilder.MaturityColors.HIDDEN_BG
            )
        }
    }

    private fun createFilterTag(maturity: FeatureMaturity, label: String, color: Color) {
        val tag = MaturityFilterTag(
            maturity = maturity,
            label = label,
            activeColor = color,
            initiallySelected = maturity == selectedMaturity
        ) { m, selected ->
            if (!selected) {
                filterTags[m]?.setSelected(true)
                return@MaturityFilterTag
            }
            selectedMaturity = m
            filterTags.forEach { (key, item) -> item.setSelected(key == selectedMaturity) }
            onFilterChanged(setOf(selectedMaturity))
        }
        filterTags[maturity] = tag
        add(tag)
    }

    /**
     * Returns the currently selected maturity levels.
     */
    fun getSelectedMaturities(): Set<FeatureMaturity> = setOf(selectedMaturity)

    /**
     * Sets the selected maturity levels programmatically.
     */
    fun setSelectedMaturities(maturities: Set<FeatureMaturity>) {
        selectedMaturity = maturities.firstOrNull() ?: selectedMaturity
        filterTags.forEach { (maturity, tag) ->
            tag.setSelected(maturity == selectedMaturity)
        }
    }

    /**
     * Selects all maturity levels.
     */
    fun selectAll() {
        setSelectedMaturities(setOf(selectedMaturity))
        onFilterChanged(setOf(selectedMaturity))
    }

    /**
     * Deselects all maturity levels.
     */
    fun deselectAll() {
        setSelectedMaturities(setOf(selectedMaturity))
        onFilterChanged(setOf(selectedMaturity))
    }
}
