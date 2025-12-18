package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.python.PythonVersionGuard
import com.github.chbndrhnns.intellijplatformplugincopy.services.PythonStdlibService
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFile

class PyShadowingStdlibModuleInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
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
                        ProblemHighlightType.WEAK_WARNING
                    )
                }
            }
        }
    }
}
