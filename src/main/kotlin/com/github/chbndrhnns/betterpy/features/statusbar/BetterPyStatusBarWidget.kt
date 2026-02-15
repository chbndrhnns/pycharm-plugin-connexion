package com.github.chbndrhnns.betterpy.features.statusbar

import com.github.chbndrhnns.betterpy.core.python.PythonVersionGuard
import com.github.chbndrhnns.betterpy.featureflags.FeatureRegistry
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsConfigurable
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.*
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon

class BetterPyStatusBarWidget(
    private val project: Project,
    scope: CoroutineScope
) : IconWidgetPresentation {

    companion object {
        const val ID = "BetterPyStatusBarWidget"

        private val ICON: Icon =
            IconLoader.getIcon("/icons/betterpy_statusbar.svg", BetterPyStatusBarWidget::class.java)
        private val ICON_DISABLED: Icon =
            IconLoader.getIcon("/icons/betterpy_statusbar_disabled.svg", BetterPyStatusBarWidget::class.java)
    }

    private val iconState = MutableStateFlow(currentIcon())
    private val toggleIncubatingActionId =
        "com.github.chbndrhnns.betterpy.features.actions.ToggleIncubatingFeaturesAction"

    init {
        val connection = project.messageBus.connect()
        connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                updateIcon()
            }
        })
        val job = scope.coroutineContext[Job]
        if (job == null) {
            connection.disconnect()
        } else {
            job.invokeOnCompletion { connection.disconnect() }
        }
    }

    override fun icon(): Flow<Icon?> = iconState

    override suspend fun getTooltipText(): String {
        val settings = PluginSettingsState.instance()
        return if (!isEnvironmentSupported()) {
            "BetterPy (disabled: Python ${PythonVersionGuard.minVersionString()}+ required)"
        } else if (settings.isMuted()) {
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
        val step = object : BaseListPopupStep<PopupAction>("BetterPy", getPopupActions()) {
            private var lastSelectedValue: PopupAction? = null

            override fun isSelectable(value: PopupAction?): Boolean = value?.enabled == true

            override fun getTextFor(value: PopupAction): String = value.label

            override fun onChosen(selectedValue: PopupAction?, finalChoice: Boolean): PopupStep<*>? {
                lastSelectedValue = selectedValue
                return FINAL_CHOICE
            }

            override fun getFinalRunnable(): Runnable? {
                val label = lastSelectedValue?.label
                return when {
                    label == "Copy Diagnostic Data" -> Runnable { invokeCopyDiagnosticDataAction() }
                    label == "Settings" -> Runnable { invokeShowSettingsAction() }
                    label?.startsWith("Disable all features") == true -> Runnable {
                        PluginSettingsState.instance().mute()
                        updateIcon()
                    }
                    label == "Enable all features" -> Runnable {
                        PluginSettingsState.instance().unmute()
                        updateIcon()
                    }
                    label?.startsWith("Turn incubating features ") == true ->
                        Runnable {
                            PluginSettingsState.instance().toggleIncubatingFeatures()
                            updateIcon()
                        }
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

    internal fun getPopupActions(): List<PopupAction> {
        val actions = mutableListOf(
            PopupAction("Copy Diagnostic Data"),
            PopupAction("Settings")
        )
        val settings = PluginSettingsState.instance()
        if (settings.isMuted()) {
            actions.add(PopupAction("Enable all features"))
        } else {
            actions.add(PopupAction("Disable all features (temporary until restart)"))
        }
        val hasIncubating = FeatureRegistry.instance().getIncubatingFeatures().isNotEmpty()
        if (hasIncubating) {
            val incubatingEnabled = isEnvironmentSupported() && !settings.isMuted()
            val incubatingActive = FeatureRegistry.instance().getEnabledIncubatingFeatures().isNotEmpty()
            val incubatingLabel = if (incubatingActive) {
                "Turn incubating features off (temporary until restart)"
            } else {
                "Turn incubating features on"
            }
            actions.add(PopupAction(incubatingLabel, incubatingEnabled))
        }
        return actions
    }

    internal fun invokeShowSettingsAction(showSettingsUtil: ShowSettingsUtil = ShowSettingsUtil.getInstance()) {
        showSettingsUtil.showSettingsDialog(project, PluginSettingsConfigurable::class.java)
    }

    private fun invokeCopyDiagnosticDataAction() {
        invokeActionById("com.github.chbndrhnns.betterpy.features.actions.CopyDiagnosticDataAction")
    }

    private fun invokeToggleIncubatingFeaturesAction() {
        invokeActionById(toggleIncubatingActionId)
    }

    private fun invokeActionById(actionId: String) {
        val action = ActionManager.getInstance().getAction(actionId) ?: return
        val dataContext = DataContext { dataId ->
            if (com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.name == dataId) project else null
        }
        val event = AnActionEvent.createEvent(action, dataContext, null, "BetterPyStatusBar", ActionUiKind.NONE, null)
        ActionUtil.performAction(action, event)
    }
}

internal data class PopupAction(
    val label: String,
    val enabled: Boolean = true
)

class BetterPyStatusBarWidgetFactory : StatusBarWidgetFactory, WidgetPresentationFactory {

    override fun getId(): String = BetterPyStatusBarWidget.ID

    override fun getDisplayName(): String = "BetterPy"

    override fun isAvailable(project: Project): Boolean = true

    override fun createPresentation(
        context: WidgetPresentationDataContext,
        scope: CoroutineScope
    ): WidgetPresentation {
        return BetterPyStatusBarWidget(context.project, scope)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        // Widget disposal is handled by the widget itself
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
