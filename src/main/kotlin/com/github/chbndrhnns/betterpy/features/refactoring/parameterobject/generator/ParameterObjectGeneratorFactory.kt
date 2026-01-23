package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject.generator

import com.github.chbndrhnns.betterpy.features.refactoring.parameterobject.ParameterObjectBaseType

/**
 * Factory for creating parameter object generators based on the selected base type.
 */
object ParameterObjectGeneratorFactory {

    /**
     * Returns the appropriate generator for the given base type.
     */
    fun getGenerator(baseType: ParameterObjectBaseType): ParameterObjectGenerator {
        return when (baseType) {
            ParameterObjectBaseType.DATACLASS -> DataclassGenerator()
            ParameterObjectBaseType.NAMED_TUPLE -> NamedTupleGenerator()
            ParameterObjectBaseType.TYPED_DICT -> TypedDictGenerator()
            ParameterObjectBaseType.PYDANTIC_BASE_MODEL -> PydanticGenerator()
        }
    }
}
