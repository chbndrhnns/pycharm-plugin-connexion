package com.github.chbndrhnns.betterpy.features.intentions.annotations

import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.util.containers.MultiMap
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext

internal object CopyTypeAnnotationsFromParentSupport {

    fun showConflictsDialog(project: Project, conflicts: MultiMap<PsiElement, String>, title: String): Boolean {
        if (conflicts.isEmpty) return true

        var result = false
        val app = ApplicationManager.getApplication()
        val showDialog = {
            val dialog = ConflictsDialog(project, conflicts, null)
            dialog.title = title
            result = dialog.showAndGet()
        }
        if (app.isDispatchThread) {
            showDialog()
        } else {
            app.invokeAndWait(showDialog)
        }
        return result
    }

    fun buildCopyPlan(function: PyFunction): CopyPlan? {
        val context = TypeEvalContext.codeInsightFallback(function.project)
        val superMethods = PySuperMethodsSearch.search(function, true, context)
            .findAll()
            .filterIsInstance<PyFunction>()
            .filter { it.isOwnCode() }
        val sourceFunction = superMethods.firstOrNull { hasTypeAnnotations(it) }
            ?: if (superMethods.isNotEmpty()) return null else function

        if (!sourceFunction.isOwnCode()) return null

        val sourceAnnotations = collectTypeAnnotations(sourceFunction)
        if (!sourceAnnotations.hasAnyAnnotations) return null

        val targets = if (sourceFunction != function) {
            listOf(function).filter { it.isOwnCode() }
        } else {
            PyOverridingMethodsSearch.search(function, true)
                .findAll()
                .filterIsInstance<PyFunction>()
                .filter { it.isOwnCode() }
        }

        if (targets.isEmpty()) return null

        val conflicts = MultiMap.create<PsiElement, String>()
        val targetPlans = targets.mapNotNull { target ->
            buildTargetPlan(target, sourceAnnotations, conflicts)
        }

        if (targetPlans.isEmpty()) return null

        return CopyPlan(sourceFunction, targetPlans, conflicts)
    }

    fun applyPlan(project: Project, plan: CopyPlan) {
        for (targetPlan in plan.targets) {
            for (paramUpdate in targetPlan.parameters) {
                updateParameterAnnotation(project, paramUpdate.parameter, paramUpdate.annotationText)
            }
            targetPlan.returnAnnotation?.let { updateReturnAnnotation(project, targetPlan.target, it) }
        }
    }

    private fun buildTargetPlan(
        target: PyFunction,
        sourceAnnotations: SourceAnnotations,
        conflicts: MultiMap<PsiElement, String>
    ): TargetPlan? {
        val parameterUpdates = mutableListOf<ParameterUpdate>()
        for ((name, annotationText) in sourceAnnotations.parameterAnnotations) {
            val targetParam = target.parameterList.parameters
                .firstOrNull { it.name == name } as? PyNamedParameter
            if (targetParam != null) {
                val existing = targetParam.annotation?.value?.text
                when {
                    existing == null -> parameterUpdates.add(ParameterUpdate(targetParam, annotationText))
                    existing == annotationText -> Unit
                    else -> {
                        val targetName = target.name ?: "<anonymous>"
                        conflicts.putValue(
                            target,
                            "Method '$targetName' parameter '$name' has $existing, will be replaced with $annotationText."
                        )
                        parameterUpdates.add(ParameterUpdate(targetParam, annotationText))
                    }
                }
            }
        }

        var returnUpdate: String? = null
        sourceAnnotations.returnAnnotation?.let { sourceReturn ->
            val existingReturn = target.annotation?.value?.text
            when {
                existingReturn == null -> returnUpdate = sourceReturn
                existingReturn == sourceReturn -> Unit
                else -> {
                    val targetName = target.name ?: "<anonymous>"
                    conflicts.putValue(
                        target,
                        "Method '$targetName' return annotation has $existingReturn, will be replaced with $sourceReturn."
                    )
                    returnUpdate = sourceReturn
                }
            }
        }

        if (parameterUpdates.isEmpty() && returnUpdate == null) return null
        return TargetPlan(target, parameterUpdates, returnUpdate)
    }

    private fun collectTypeAnnotations(function: PyFunction): SourceAnnotations {
        val paramAnnotations = function.parameterList.parameters.asSequence()
            .filterIsInstance<PyNamedParameter>()
            .mapNotNull { param ->
                val name = param.name ?: return@mapNotNull null
                val annotationText = param.annotation?.value?.text ?: return@mapNotNull null
                name to annotationText
            }
            .toMap()
        val returnAnnotation = function.annotation?.value?.text
        return SourceAnnotations(paramAnnotations, returnAnnotation)
    }

    private fun hasTypeAnnotations(function: PyFunction): Boolean {
        if (function.annotation != null) return true
        return function.parameterList.parameters.any { it is PyNamedParameter && it.annotation != null }
    }

    private fun updateParameterAnnotation(project: Project, parameter: PyNamedParameter, annotationText: String) {
        val paramName = parameter.name ?: return
        val generator = PyElementGenerator.getInstance(project)
        val newParam = generator.createParameter(
            paramName,
            parameter.defaultValueText,
            annotationText,
            LanguageLevel.forElement(parameter)
        )
        parameter.replace(newParam)
    }

    private fun updateReturnAnnotation(project: Project, function: PyFunction, annotationText: String) {
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(function)
        val dummyFunction = generator.createFromText(
            languageLevel,
            PyFunction::class.java,
            "def foo() -> $annotationText: pass"
        )
        val newAnnotation = dummyFunction.annotation ?: return
        val existing = function.annotation
        if (existing != null) {
            existing.replace(newAnnotation)
        } else {
            val anchor = function.parameterList
            function.addAfter(newAnnotation, anchor)
        }
    }

    data class ParameterUpdate(
        val parameter: PyNamedParameter,
        val annotationText: String
    )

    data class SourceAnnotations(
        val parameterAnnotations: Map<String, String>,
        val returnAnnotation: String?
    ) {
        val hasAnyAnnotations: Boolean =
            parameterAnnotations.isNotEmpty() || returnAnnotation != null
    }

    data class TargetPlan(
        val target: PyFunction,
        val parameters: List<ParameterUpdate>,
        val returnAnnotation: String?
    )

    data class CopyPlan(
        val source: PyFunction,
        val targets: List<TargetPlan>,
        val conflicts: MultiMap<PsiElement, String>
    ) {
        val hasChanges: Boolean = targets.isNotEmpty()
    }
}
