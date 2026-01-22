package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring.parameterobject

import com.github.chbndrhnns.intellijplatformplugincopy.core.MyBundle
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyNamedParameter

class IntroduceParameterObjectValidator(
    private val scope: PsiElement
) {
    fun validate(
        className: String,
        parameterName: String,
        selectedParameters: List<PyNamedParameter>
    ): ValidationInfo? {
        if (className.isBlank()) {
            return ValidationInfo(MyBundle.message("introduce.parameter.object.validation.class.name.empty"))
        }
        if (!isValidPythonIdentifier(className)) {
            return ValidationInfo(MyBundle.message("introduce.parameter.object.validation.class.name.invalid"))
        }

        if (parameterName.isBlank()) {
            return ValidationInfo(MyBundle.message("introduce.parameter.object.validation.parameter.name.empty"))
        }
        if (!isValidPythonIdentifier(parameterName)) {
            return ValidationInfo(MyBundle.message("introduce.parameter.object.validation.parameter.name.invalid"))
        }

        if (selectedParameters.isEmpty()) {
            return ValidationInfo(MyBundle.message("introduce.parameter.object.validation.parameters.empty"))
        }

        if (isNameTaken(className)) {
            return ValidationInfo(MyBundle.message("introduce.parameter.object.validation.class.name.taken", className))
        }

        return null
    }

    private fun isValidPythonIdentifier(name: String): Boolean {
        if (PyNames.isReserved(name)) return false
        return name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))
    }

    private fun isNameTaken(name: String): Boolean {
        val file = scope.containingFile as? PyFile ?: return false
        return ParameterObjectUtils.isNameTaken(file, name)
    }
}
