package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "PycharmDddToolkitSettings", storages = [Storage("pycharm-ddd-toolkit.xml")])
class PluginSettingsState : PersistentStateComponent<PluginSettingsState.State> {
    data class State(
        var enableWrapWithExpectedTypeIntention: Boolean = true,
        var enableWrapItemsWithExpectedTypeIntention: Boolean = true,
        var enableUnwrapToExpectedTypeIntention: Boolean = true,
        var enableUnwrapItemsToExpectedTypeIntention: Boolean = true,
        var enableIntroduceCustomTypeFromStdlibIntention: Boolean = true,
        var enablePopulateArgumentsIntention: Boolean = true,
        var enablePyMissingInDunderAllInspection: Boolean = true,
        var enableCopyPackageContentAction: Boolean = true,
        var enableRestoreSourceRootPrefix: Boolean = true,
        var enableRelativeImportPreference: Boolean = true,
        var enableDictAccessIntention: Boolean = true,
        var enableChangeVisibilityIntention: Boolean = true,
        var enableIntroduceParameterObjectIntention: Boolean = true,
        var enableDataclassMissingInspection: Boolean = true,
        var enablePrivateModuleImportInspection: Boolean = true,
        var enableCopyBuildNumberAction: Boolean = true,
        var enableCreateLocalVariableIntention: Boolean = true,
        var enableStructureViewPrivateMembersFilter: Boolean = true,
        var enableMakeParameterOptionalIntention: Boolean = true,
        var enableMakeParameterMandatoryIntention: Boolean = true,
        var enableAddExceptionCaptureIntention: Boolean = true,
        var enableWrapExceptionsWithParenthesesIntention: Boolean = true,
        var enableImplementAbstractMethodInChildClassesIntention: Boolean = true,
        var enableAbstractMethodNotImplementedInspection: Boolean = true,
        var enablePyReturnCompletionContributor: Boolean = true,
        var enablePyMockPatchReferenceContributor: Boolean = true,
        var enablePyFilterWarningsReferenceContributor: Boolean = true,
        var enableTypeAnnotationUsageFilteringRule: Boolean = true,
        var enablePyMessageConsoleFilter: Boolean = true,
        var enablePytestIdentifierSearchEverywhereContributor: Boolean = true,
        var enableIntroduceParameterObjectAction: Boolean = true,
        var enableIntroduceParameterObjectRefactoringAction: Boolean = true,
        var enableCopyBlockWithDependenciesAction: Boolean = true,
        var enableCopyPytestNodeIdsAction: Boolean = true,
        var enableCopyFQNsAction: Boolean = true,
        var enableCopyStacktraceAction: Boolean = true,
        var enableJumpToPytestNodeInTestTreeAction: Boolean = true,
        var enableTogglePytestSkipIntention: Boolean = true,
        var enableParametrizePytestTestIntention: Boolean = true,
        var enableStripSignatureTypeAnnotationsIntention: Boolean = true,
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
