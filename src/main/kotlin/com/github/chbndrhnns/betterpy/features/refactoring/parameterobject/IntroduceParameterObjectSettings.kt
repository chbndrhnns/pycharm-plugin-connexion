package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import com.jetbrains.python.psi.PyNamedParameter

data class IntroduceParameterObjectSettings(
    val selectedParameters: List<PyNamedParameter>,
    val className: String,
    val parameterName: String,
    val baseType: ParameterObjectBaseType = ParameterObjectBaseType.DATACLASS,
    val generateFrozen: Boolean = true,
    val generateSlots: Boolean = true,
    val generateKwOnly: Boolean = true
) {
    companion object {
        const val DEFAULT_PARAMETER_NAME = "params"
    }
}
