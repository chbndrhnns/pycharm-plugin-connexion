package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.python.PythonVersionGuard
import com.github.chbndrhnns.intellijplatformplugincopy.services.PythonStdlibService
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.util.isOwnCode
import com.intellij.codeInspection.*
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFile

class PyShadowingStdlibModuleInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        if (!PythonVersionGuard.isSatisfied(holder.project)) {
            return object : PyElementVisitor() {}
        }
        val settings = PluginSettingsState.instance().state
        if (!settings.enableShadowingStdlibModuleInspection) {
            return object : PyElementVisitor() {}
        }

        return object : PyElementVisitor() {
            override fun visitPyFile(node: PyFile) {
                super.visitPyFile(node)
                val fileName = node.name
                if (!fileName.endsWith(".py")) return
                val baseName = fileName.substringBeforeLast(".")

                val stdlibService = PythonStdlibService.getInstance(node.project)
                if (stdlibService.isStdlibModule(baseName, null)) {
                    holder.registerProblem(
                        node,
                        "File name '$fileName' shadows a Python standard library module",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        RenameFileQuickFix()
                    )
                }
            }
        }
    }

    private class RenameFileQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = PluginConstants.ACTION_PREFIX + "Rename file"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement as? PyFile ?: return
            ApplicationManager.getApplication().invokeLater {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                val dataContext = SimpleDataContext.builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(CommonDataKeys.PSI_ELEMENT, file)
                    .add(CommonDataKeys.EDITOR, editor)
                    .build()
                val handler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext)
                handler?.invoke(project, arrayOf(file), dataContext)
            }
        }
    }
}
