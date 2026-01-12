package com.github.chbndrhnns.intellijplatformplugincopy.statusbar

import com.github.chbndrhnns.intellijplatformplugincopy.python.PythonVersionGuard
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon

class BetterPyStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.IconPresentation {

    companion object {
        const val ID = "BetterPyStatusBarWidget"

        private val ICON: Icon =
            IconLoader.getIcon("/icons/betterpy_statusbar.svg", BetterPyStatusBarWidget::class.java)
        private val ICON_DISABLED: Icon =
            IconLoader.getIcon("/icons/betterpy_statusbar_disabled.svg", BetterPyStatusBarWidget::class.java)
    }

    private var statusBar: StatusBar? = null

    override fun ID(): String = ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getIcon(): Icon = if (isEnvironmentSupported()) ICON else ICON_DISABLED

    override fun getTooltipText(): String = if (isEnvironmentSupported()) {
        "BetterPy"
    } else {
        "BetterPy (disabled: Python ${PythonVersionGuard.minVersionString()}+ required)"
    }

    override fun getClickConsumer(): com.intellij.util.Consumer<MouseEvent> {
        return com.intellij.util.Consumer { event ->
            if (isEnvironmentSupported()) {
                showPopup(event)
            }
        }
    }

    internal fun isEnvironmentSupported(): Boolean = PythonVersionGuard.isSatisfied(project)

    private fun showPopup(event: MouseEvent) {
        val popup = createPopup()
        val point = Point(0, -popup.content.preferredSize.height)
        popup.show(RelativePoint(event.component, point))
    }

    private fun createPopup(): ListPopup {
        val step = object : BaseListPopupStep<String>("BetterPy", listOf("Copy Diagnostic Data")) {
            override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
                if (selectedValue == "Copy Diagnostic Data") {
                    invokeCopyDiagnosticDataAction()
                }
                return FINAL_CHOICE
            }
        }
        return JBPopupFactory.getInstance().createListPopup(step)
    }

    private fun invokeCopyDiagnosticDataAction() {
        val action = ActionManager.getInstance()
            .getAction("com.github.chbndrhnns.intellijplatformplugincopy.actions.CopyDiagnosticDataAction")
        if (action != null) {
            val dataContext = DataContext { dataId ->
                if (com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.name == dataId) project else null
            }
            val event = AnActionEvent.createEvent(action, dataContext, null, "BetterPyStatusBar", ActionUiKind.NONE, null)
            action.actionPerformed(event)
        }
    }
}

class BetterPyStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = BetterPyStatusBarWidget.ID

    override fun getDisplayName(): String = "BetterPy"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = BetterPyStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        // Widget disposal is handled by the widget itself
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
