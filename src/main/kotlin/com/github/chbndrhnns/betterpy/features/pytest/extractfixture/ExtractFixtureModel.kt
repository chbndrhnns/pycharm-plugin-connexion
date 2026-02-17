package com.github.chbndrhnns.betterpy.features.pytest.extractfixture

import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStatement

/**
 * Extraction mode: create a fixture or a helper function.
 */
enum class ExtractionMode {
    FIXTURE,
    HELPER_FUNCTION
}

/**
 * How to inject the extracted fixture into the original function.
 */
enum class InjectionMode {
    /** Add fixture as a parameter to the function signature */
    PARAMETER,

    /** Use @pytest.mark.usefixtures decorator */
    USEFIXTURES
}

/**
 * Target location for the extracted code.
 */
enum class TargetLocation {
    SAME_FILE,
    CONFTEST,
    OTHER_FILE
}

/**
 * Fixture scope for pytest fixtures.
 */
enum class FixtureScope(val value: String) {
    FUNCTION("function"),
    CLASS("class"),
    MODULE("module"),
    PACKAGE("package"),
    SESSION("session")
}

/**
 * Represents a fixture parameter used in the selected code.
 */
data class FixtureParameter(
    val name: String,
    val resolvedFixture: PyFunction? = null
)

/**
 * Model containing analysis results for extract fixture refactoring.
 */
data class ExtractFixtureModel(
    val containingFunction: PyFunction,
    val statements: List<PyStatement>,
    val usedFixtures: List<FixtureParameter>,
    val usedLocals: Set<String>,
    val definedLocals: Set<String>,
    val outputVariables: List<String>,
    val startOffset: Int,
    val endOffset: Int
)

/**
 * User choices from the dialog.
 */
data class ExtractFixtureOptions(
    val fixtureName: String,
    val extractionMode: ExtractionMode = ExtractionMode.FIXTURE,
    val targetLocation: TargetLocation = TargetLocation.SAME_FILE,
    val fixtureScope: FixtureScope = FixtureScope.FUNCTION,
    val autouse: Boolean = false,
    val injectionMode: InjectionMode = InjectionMode.PARAMETER,
    val targetFilePath: String? = null
)
