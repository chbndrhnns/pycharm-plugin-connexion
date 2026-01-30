package com.github.chbndrhnns.betterpy.featureflags

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JLabel
import javax.swing.JPanel

enum class EnabledFilter {
    ALL,
    ENABLED,
    DISABLED
}

private class EnabledFilterTag(
    private val filter: EnabledFilter,
    private val label: String,
    private val activeColor: Color,
    initiallySelected: Boolean,
    private val onSelected: (EnabledFilter) -> Unit
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
                onSelected(filter)
            }

            override fun mouseEntered(e: MouseEvent) {
                repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                repaint()
            }
        })
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
                g2.color = activeColor
                g2.fill(shape)
            } else {
                g2.color = activeColor
                g2.stroke = BasicStroke(JBUI.scale(2).toFloat())
                g2.draw(shape)
            }

            if (getMousePosition() != null) {
                g2.color = Color(255, 255, 255, 30)
                g2.fill(shape)
            }
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}

class EnabledFilterPanel(
    initialSelection: EnabledFilter = EnabledFilter.ALL,
    private val onFilterChanged: (EnabledFilter) -> Unit
) : JPanel() {

    private val filterTags = mutableMapOf<EnabledFilter, EnabledFilterTag>()
    private var selectedFilter: EnabledFilter = initialSelection

    init {
        layout = FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4))
        isOpaque = false
        border = JBUI.Borders.empty(4, 0, 4, 0)

        add(JLabel("Show:").apply {
            foreground = JBColor.GRAY
        })

        createFilterTag(EnabledFilter.ALL, "All", JBColor.GRAY)
        createFilterTag(EnabledFilter.ENABLED, "Enabled", JBColor(Color(76, 175, 80), Color(76, 175, 80)))
        createFilterTag(EnabledFilter.DISABLED, "Disabled", JBColor(Color(244, 67, 54), Color(244, 67, 54)))
    }

    private fun createFilterTag(filter: EnabledFilter, label: String, color: Color) {
        val tag = EnabledFilterTag(
            filter = filter,
            label = label,
            activeColor = color,
            initiallySelected = filter == selectedFilter
        ) { selected ->
            if (selectedFilter == selected) return@EnabledFilterTag
            selectedFilter = selected
            filterTags.forEach { (key, item) -> item.setSelected(key == selectedFilter) }
            onFilterChanged(selectedFilter)
        }
        filterTags[filter] = tag
        add(tag)
    }

    fun getSelectedFilter(): EnabledFilter = selectedFilter
}
