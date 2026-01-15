package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyNamedParameter

class IntroduceParameterObjectValidator(
    private val project: Project,
    private val scope: PsiElement
) {
    fun validate(
        className: String,
        parameterName: String,
        selectedParameters: List<PyNamedParameter>
    ): ValidationInfo? {
        if (className.isBlank()) {
            return ValidationInfo("Class name cannot be empty")
        }
        if (!isValidPythonIdentifier(className)) {
            return ValidationInfo("Class name must be a valid Python identifier")
        }

        if (parameterName.isBlank()) {
            return ValidationInfo("Parameter name cannot be empty")
        }
        if (!isValidPythonIdentifier(parameterName)) {
            return ValidationInfo("Parameter name must be a valid Python identifier")
        }

        if (selectedParameters.isEmpty()) {
            return ValidationInfo("At least one parameter must be selected")
        }

        if (isNameTaken(className)) {
            return ValidationInfo("Class name '$className' is already in use")
        }

        return null
    }

    private fun isValidPythonIdentifier(name: String): Boolean {
        if (PyNames.isReserved(name)) return false
        return name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))
    }

    private fun isNameTaken(name: String): Boolean {
        val file = scope.containingFile as? PyFile ?: return false

        if (file.findTopLevelClass(name) != null) return true
        if (file.findTopLevelFunction(name) != null) return true
        if (file.findTopLevelAttribute(name) != null) return true

        for (stmt in file.importBlock) {
            for (element in stmt.importElements) {
                val visibleName = element.asName ?: element.importedQName?.lastComponent
                if (visibleName == name) return true
            }
        }
        return false
    }
}
