package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Persistent settings state for the plugin.
 * Settings are organized by feature category for easier navigation.
 */
@State(name = "PycharmDddToolkitSettings", storages = [Storage("pycharm-ddd-toolkit.xml")])
class PluginSettingsState : PersistentStateComponent<PluginSettingsState.State> {
    data class State(
        // ---- Type Wrapping/Unwrapping Intentions ----
        var enableWrapWithExpectedTypeIntention: Boolean = true,
        var enableWrapItemsWithExpectedTypeIntention: Boolean = true,
        var enableUnwrapToExpectedTypeIntention: Boolean = true,
        var enableUnwrapItemsToExpectedTypeIntention: Boolean = true,
        var enableIntroduceCustomTypeFromStdlibIntention: Boolean = true,

        // ---- Type Bucket Priority Settings ----
        /** When true, always prefer OWN (project) types over STDLIB types in union wrapping. */
        var preferOwnTypesInUnionWrapping: Boolean = true,
        /** When true, include STDLIB types as candidates in union wrapping (otherwise filter them out). */
        var includeStdlibInUnionWrapping: Boolean = true,

        // ---- Parameter & Argument Intentions ----
        var enablePopulateArgumentsIntention: Boolean = true,
        var enableMakeParameterOptionalIntention: Boolean = true,
        var enableMakeParameterMandatoryIntention: Boolean = true,
        var enableCreateLocalVariableIntention: Boolean = true,

        // ---- Code Structure Intentions ----
        var enableDictAccessIntention: Boolean = true,
        var enableChangeVisibilityIntention: Boolean = true,
        var enableCallableToProtocolIntention: Boolean = true,
        var enableAddExceptionCaptureIntention: Boolean = true,
        var enableWrapExceptionsWithParenthesesIntention: Boolean = true,
        var enableStripSignatureTypeAnnotationsIntention: Boolean = true,

        // ---- Abstract Method Intentions ----
        var enableImplementAbstractMethodInChildClassesIntention: Boolean = true,
        var enableMakeMemberAbstractInAbstractClassIntention: Boolean = true,

        // ---- Pytest Intentions ----
        var enableTogglePytestSkipIntention: Boolean = true,
        var enableParametrizePytestTestIntention: Boolean = true,
        var enableConvertPytestParamIntention: Boolean = true,
        var enablePyMockTypeProvider: Boolean = true,

        // ---- Inspections ----
        var enablePyMissingInDunderAllInspection: Boolean = true,
        var enableDataclassMissingInspection: Boolean = true,
        var enablePrivateModuleImportInspection: Boolean = true,
        var enableAbstractMethodNotImplementedInspection: Boolean = true,
        var enableConstantFinalInspection: Boolean = true,
        var enableShadowingStdlibModuleInspection: Boolean = true,
        var enableUnresolvedReferenceAsErrorInspection: Boolean = true,

        // ---- Copy/Clipboard Actions ----
        var enableCopyPackageContentAction: Boolean = true,
        var enableCopyBuildNumberAction: Boolean = true,
        var enableCopyBlockWithDependenciesAction: Boolean = true,
        var enableCopyPytestNodeIdsAction: Boolean = true,
        var enableCopyPytestNodeIdFromEditorAction: Boolean = true,
        var enableCopyFQNsAction: Boolean = true,
        var enableCopyStacktraceAction: Boolean = true,

        // ---- Refactoring Actions ----
        var enableIntroduceParameterObjectRefactoringAction: Boolean = true,
        var enableInlineParameterObjectRefactoringAction: Boolean = true,

        // ---- Parameter Object Settings ----
        /** Default base type for parameter objects (dataclass, NamedTuple, TypedDict, pydantic.BaseModel) */
        var defaultParameterObjectBaseType: String = "dataclass",

        // ---- Pytest Tree Actions ----
        var enableJumpToPytestNodeInTestTreeAction: Boolean = true,
        var enableTogglePytestSkipFromTestTreeAction: Boolean = true,

        // ---- Completion & Reference Contributors ----
        var enablePyReturnCompletionContributor: Boolean = true,
        var enablePyMockPatchReferenceContributor: Boolean = true,
        var enablePyFilterWarningsReferenceContributor: Boolean = true,
        var enablePytestIdentifierSearchEverywhereContributor: Boolean = true,
        var enableToggleTypeAliasIntention: Boolean = true,
        var enableExportSymbolToTargetIntention: Boolean = true,

        // ---- Import & Structure View Settings ----
        var enableRestoreSourceRootPrefix: Boolean = true,
        var enableRelativeImportPreference: Boolean = true,
        var enableStructureViewPrivateMembersFilter: Boolean = true,
        var enableHideTransientImports: Boolean = true,

        // ---- Filters & Suppressors ----
        var enableTypeAnnotationUsageFilteringRule: Boolean = true,
        var enablePyMessageConsoleFilter: Boolean = true,
        var suppressSuggestedRefactoringSignatureChangeIntention: Boolean = true,
        var enableRenameToSelfFilter: Boolean = true,

        // ---- Connexion ----
        var enableConnexionInspections: Boolean = true,
        var enableConnexionCompletion: Boolean = true,
        var enableNewTypeTypeVarParamSpecRename: Boolean = true,
        var enablePyPackageRunConfigurationAction: Boolean = true,
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        @JvmStatic
        fun instance(): PluginSettingsState = service()
    }
}
