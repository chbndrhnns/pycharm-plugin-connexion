package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import com.github.chbndrhnns.betterpy.featureflags.Feature
import com.github.chbndrhnns.betterpy.featureflags.FeatureCategory

/**
 * Feature settings for parameter object refactoring.
 * These settings are discovered by FeatureRegistry via reflection.
 */
data class ParameterObjectFeatureSettings(
    @Feature(
        id = "parameter-object-refactoring",
        displayName = "Parameter object refactoring",
        description = "Enables introduce/inline parameter object refactoring actions",
        category = FeatureCategory.ACTIONS,
        loggingCategories = [
            "com.github.chbndrhnns.betterpy.features.refactoring.parameterobject",
        ],
        youtrackIssues = [
            "PY-59270",
        ]
    )
    var enableParameterObjectRefactoring: Boolean = true,

    @Feature(
        id = "parameter-object-gutter-icon",
        displayName = "Parameter object gutter icon",
        description = "Shows a gutter icon on functions that are candidates for 'Introduce Parameter Object' refactoring",
        category = FeatureCategory.ACTIONS
    )
    var enableParameterObjectGutterIcon: Boolean = true,

    @Feature(
        id = "parameter-object-mcp-tool",
        displayName = "Parameter object MCP tool",
        description = "Enables MCP (Model Context Protocol) tools for parameter object refactoring",
        category = FeatureCategory.ACTIONS
    )
    var enableParameterObjectMcpTool: Boolean = true,

    /** Default base type for parameter objects (dataclass, NamedTuple, TypedDict, pydantic.BaseModel) */
    var defaultParameterObjectBaseType: String = "dataclass",
)
