package com.github.chbndrhnns.intellijplatformplugincopy.features.connexion

import com.github.chbndrhnns.intellijplatformplugincopy.core.util.isOwnCode
import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonElementVisitor
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YamlPsiElementVisitor

class ConnexionJsonInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        if (!PluginSettingsState.instance().state.enableConnexionInspections) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        return object : JsonElementVisitor() {
            override fun visitStringLiteral(literal: JsonStringLiteral) {
                checkReferences(literal, holder)
            }
        }
    }
}

class ConnexionYamlInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        if (!PluginSettingsState.instance().state.enableConnexionInspections) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        return object : YamlPsiElementVisitor() {
            override fun visitScalar(scalar: YAMLScalar) {
                checkReferences(scalar, holder)
            }
        }
    }
}

private fun checkReferences(element: PsiElement, holder: ProblemsHolder) {
    for (ref in element.references) {
        if (ref is ConnexionReferenceBase || ref is ConnexionControllerReference) {
            if (ref.resolve() == null) {
                val description = if (ref is ConnexionReferenceBase)
                    ConnexionConstants.UNRESOLVED_OPERATION_ID
                else
                    ConnexionConstants.UNRESOLVED_CONTROLLER

                holder.registerProblem(
                    ref.element,
                    description,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    ref.rangeInElement
                )
            }
        }
    }
}
