package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import com.github.chbndrhnns.betterpy.featureflags.Feature
import com.github.chbndrhnns.betterpy.featureflags.FeatureDefaults

/**
 * Feature settings for parameter object refactoring.
 * These settings are discovered by FeatureRegistry via reflection.
 */
data class ParameterObjectFeatureSettings(
    @Feature("parameter-object-refactoring")
    var enableParameterObjectRefactoring: Boolean = FeatureDefaults.defaultEnabled("parameter-object-refactoring"),

    @Feature("parameter-object-gutter-icon")
    var enableParameterObjectGutterIcon: Boolean = FeatureDefaults.defaultEnabled("parameter-object-gutter-icon"),

    @Feature("parameter-object-mcp-tool")
    var enableParameterObjectMcpTool: Boolean = FeatureDefaults.defaultEnabled("parameter-object-mcp-tool"),

    /** Default base type for parameter objects (dataclass, NamedTuple, TypedDict, pydantic.BaseModel) */
    var defaultParameterObjectBaseType: String = "dataclass",

    /** Test hook to skip the refactoring dialog and accept defaults. */
    var autoAcceptRefactoringDialog: Boolean = false,
)
