package com.github.chbndrhnns.betterpy.features.type

import com.jetbrains.python.psi.types.*
import java.util.*

/**
 * Extension to make PyMockType compatible with its spec type.
 * When expected type is X and actual type is Mock(spec=X), this should match.
 */
class PyMockTypeCheckerExtension : PyTypeCheckerExtension {
    override fun match(
        expected: PyType?,
        actual: PyType?,
        context: TypeEvalContext,
        substitutions: PyTypeChecker.GenericSubstitutions
    ): Optional<Boolean> {
        if (actual is PyMockType && expected != null) {
            val specType = actual.specType ?: return Optional.empty()
            // If the spec type matches the expected type, the mock is compatible
            if (specType is PyClassLikeType && expected is PyClassLikeType) {
                // Check if spec type is the same as or a subtype of expected
                if (specType.classQName == expected.classQName) {
                    return Optional.of(true)
                }
                // Check ancestors
                val ancestors = specType.getAncestorTypes(context)
                for (ancestor in ancestors) {
                    if (ancestor?.classQName == expected.classQName) {
                        return Optional.of(true)
                    }
                }
            }
            // Direct type match
            if (PyTypeChecker.match(expected, specType, context)) {
                return Optional.of(true)
            }
        }
        return Optional.empty()
    }
}
