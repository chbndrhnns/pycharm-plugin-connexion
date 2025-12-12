package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest.PytestSkipToggler
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.execution.testframework.TestTreeView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFile
import javax.swing.tree.DefaultMutableTreeNode

class TogglePytestSkipFromTestTreeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        if (!PluginSettingsState.instance().state.enableTogglePytestSkipFromTestTreeAction) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val view = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? TestTreeView
        if (view == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val project = e.project
        val selectionPaths = view.selectionPaths
        if (project == null || selectionPaths.isNullOrEmpty()) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val hasTarget = ReadAction.compute<Boolean, RuntimeException> {
            selectionPaths.any { path ->
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@any false
                TogglePytestSkipFromTestTreeTargetResolver.resolve(node, project) != null
            }
        }

        e.presentation.isEnabledAndVisible = hasTarget
    }

    override fun actionPerformed(e: AnActionEvent) {
        val view = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? TestTreeView ?: return
        val project = e.project ?: return

        val modalityState = ModalityState.defaultModalityState()

        val selectionPaths = view.selectionPaths ?: return

        data class Resolved(
            val target: TogglePytestSkipFromTestTreeTargetResolver.Target,
            val pointer: SmartPsiElementPointer<*>
        )

        ReadAction
            .nonBlocking<List<Resolved>> {
                val pointerManager = SmartPointerManager.getInstance(project)
                selectionPaths.mapNotNull { path ->
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@mapNotNull null
                    val target =
                        TogglePytestSkipFromTestTreeTargetResolver.resolve(node, project) ?: return@mapNotNull null

                    val elementForPointer = when (target) {
                        is TogglePytestSkipFromTestTreeTargetResolver.Target.Function -> target.function
                        is TogglePytestSkipFromTestTreeTargetResolver.Target.Clazz -> target.clazz
                        is TogglePytestSkipFromTestTreeTargetResolver.Target.Module -> target.file
                    }
                    Resolved(target, pointerManager.createSmartPsiElementPointer(elementForPointer))
                }
            }
            .expireWith(project)
            .finishOnUiThread(modalityState) { resolved ->
                if (resolved.isEmpty()) return@finishOnUiThread

                // Write actions from toolwindow actions must run in a write-safe transaction context.
                // Wrapping into TransactionGuard avoids "Write-unsafe context" errors.
                TransactionGuard.getInstance().submitTransaction(project, null, Runnable {
                    WriteCommandAction.runWriteCommandAction(project) {
                        val toggler = PytestSkipToggler(PyElementGenerator.getInstance(project))
                        val seen = HashSet<Any>()

                        for (item in resolved) {
                            val element = item.pointer.element ?: continue

                            val key = when (item.target) {
                                is TogglePytestSkipFromTestTreeTargetResolver.Target.Function -> element
                                is TogglePytestSkipFromTestTreeTargetResolver.Target.Clazz -> element
                                is TogglePytestSkipFromTestTreeTargetResolver.Target.Module -> (element as? PyFile)
                                    ?: element
                            }
                            if (!seen.add(key)) continue

                            when (item.target) {
                                is TogglePytestSkipFromTestTreeTargetResolver.Target.Function -> {
                                    val fn = element as? com.jetbrains.python.psi.PyFunction ?: continue
                                    val file = fn.containingFile as? PyFile ?: continue
                                    toggler.toggleOnFunction(fn, file)
                                }

                                is TogglePytestSkipFromTestTreeTargetResolver.Target.Clazz -> {
                                    val cls = element as? com.jetbrains.python.psi.PyClass ?: continue
                                    val file = cls.containingFile as? PyFile ?: continue
                                    toggler.toggleOnClass(cls, file)
                                }

                                is TogglePytestSkipFromTestTreeTargetResolver.Target.Module -> {
                                    val file = element as? PyFile ?: continue
                                    toggler.toggleOnModule(file)
                                }
                            }
                        }
                    }
                })
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }
}
