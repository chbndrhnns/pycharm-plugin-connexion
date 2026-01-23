package com.github.chbndrhnns.betterpy.features.statusbar

import com.github.chbndrhnns.betterpy.core.python.PythonVersionGuard
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsConfigurable
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.*
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon

class BetterPyStatusBarWidget(private val project: Project) : IconWidgetPresentation {

    companion object {
        const val ID = "BetterPyStatusBarWidget"

        private val ICON: Icon =
            IconLoader.getIcon("/icons/betterpy_statusbar.svg", BetterPyStatusBarWidget::class.java)
        private val ICON_DISABLED: Icon =
            IconLoader.getIcon("/icons/betterpy_statusbar_disabled.svg", BetterPyStatusBarWidget::class.java)
    }

    private val iconState = MutableStateFlow(currentIcon())

    override fun icon(): Flow<Icon?> = iconState

    override suspend fun getTooltipText(): String {
        return if (!isEnvironmentSupported()) {
            "BetterPy (disabled: Python ${PythonVersionGuard.minVersionString()}+ required)"
        } else if (PluginSettingsState.instance().isMuted()) {
            "BetterPy (Muted)"
        } else {
            "BetterPy"
        }
    }

    override fun getClickConsumer(): ((MouseEvent) -> Unit)? {
        return { event ->
            if (isEnvironmentSupported()) showPopup(event)
        }
    }

    internal fun isEnvironmentSupported(): Boolean = PythonVersionGuard.isSatisfied(project)

    private fun showPopup(event: MouseEvent) {
        val popup = createPopup()
        val point = Point(0, -popup.content.preferredSize.height)
        popup.show(RelativePoint(event.component, point))
    }

    private fun createPopup(): ListPopup {
        val step = object : BaseListPopupStep<String>("BetterPy", getPopupActions()) {
            private var lastSelectedValue: String? = null

            override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
                lastSelectedValue = selectedValue
                when (selectedValue) {
                    "Disable all features" -> {
                        PluginSettingsState.instance().mute()
                        updateIcon()
                    }

                    "Enable all features" -> {
                        PluginSettingsState.instance().unmute()
                        updateIcon()
                    }
                }
                return FINAL_CHOICE
            }

            override fun getFinalRunnable(): Runnable? {
                return when (lastSelectedValue) {
                    "Copy Diagnostic Data" -> Runnable { invokeCopyDiagnosticDataAction() }
                    "Settings" -> Runnable { invokeShowSettingsAction() }
                    else -> null
                }
            }
        }
        return JBPopupFactory.getInstance().createListPopup(step)
    }

    private fun updateIcon() {
        iconState.value = currentIcon()
    }

    private fun currentIcon(): Icon {
        return if (!isEnvironmentSupported() || PluginSettingsState.instance().isMuted()) {
            ICON_DISABLED
        } else {
            ICON
        }
    }

    internal fun getPopupActions(): List<String> {
        val actions = mutableListOf("Copy Diagnostic Data", "Settings")
        val settings = PluginSettingsState.instance()
        if (settings.isMuted()) {
            actions.add("Enable all features")
        } else {
            actions.add("Disable all features")
        }
        return actions
    }

    internal fun invokeShowSettingsAction(showSettingsUtil: ShowSettingsUtil = ShowSettingsUtil.getInstance()) {
        showSettingsUtil.showSettingsDialog(project, PluginSettingsConfigurable::class.java)
    }

    private fun invokeCopyDiagnosticDataAction() {
        val action = ActionManager.getInstance()
            .getAction("com.github.chbndrhnns.betterpy.features.actions.CopyDiagnosticDataAction")
        if (action != null) {
            val dataContext = DataContext { dataId ->
                if (com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.name == dataId) project else null
            }
            val event = AnActionEvent.createEvent(action, dataContext, null, "BetterPyStatusBar", ActionUiKind.NONE, null)
            action.actionPerformed(event)
        }
    }
}

class BetterPyStatusBarWidgetFactory : StatusBarWidgetFactory, WidgetPresentationFactory {

    override fun getId(): String = BetterPyStatusBarWidget.ID

    override fun getDisplayName(): String = "BetterPy"

    override fun isAvailable(project: Project): Boolean = true

    override fun createPresentation(
        context: WidgetPresentationDataContext,
        scope: CoroutineScope
    ): WidgetPresentation {
        return BetterPyStatusBarWidget(context.project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        // Widget disposal is handled by the widget itself
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
