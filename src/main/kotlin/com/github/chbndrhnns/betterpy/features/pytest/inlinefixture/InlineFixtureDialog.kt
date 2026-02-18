package com.github.chbndrhnns.betterpy.features.pytest.inlinefixture

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

class InlineFixtureDialog(
    project: Project,
    private val fixtureName: String,
    private val usageCount: Int,
    defaultMode: InlineMode
) : DialogWrapper(project) {

    private val inlineAllRadio = JRadioButton(
        "Inline all usages and remove fixture '$fixtureName'",
        defaultMode == InlineMode.INLINE_ALL_AND_REMOVE
    )
    private val inlineThisRadio = JRadioButton(
        "Inline this usage only (keep fixture)",
        defaultMode == InlineMode.INLINE_THIS_ONLY
    )

    init {
        title = "Inline Pytest Fixture"
        init()

        val group = ButtonGroup()
        group.add(inlineAllRadio)
        group.add(inlineThisRadio)

        if (usageCount <= 1) {
            inlineThisRadio.isEnabled = false
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val options = JPanel(GridLayout(2, 1))
        options.add(inlineAllRadio)
        options.add(inlineThisRadio)
        panel.add(options, BorderLayout.CENTER)
        return panel
    }

    fun getOptions(): InlineFixtureOptions {
        val mode = if (inlineAllRadio.isSelected) {
            InlineMode.INLINE_ALL_AND_REMOVE
        } else {
            InlineMode.INLINE_THIS_ONLY
        }
        return InlineFixtureOptions(inlineMode = mode)
    }
}
