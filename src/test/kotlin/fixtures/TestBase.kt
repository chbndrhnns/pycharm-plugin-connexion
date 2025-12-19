package fixtures

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.jetbrains.python.inspections.PyTypeCheckerInspection

abstract class TestBase : MyPlatformTestCase() {
    override fun setUp() {
        PythonTestSetup.configurePythonHelpers()
        super.setUp()
        // Reset plugin settings to a known baseline after the IntelliJ test application is initialized
        val svc = PluginSettingsState.instance()
        svc.loadState(
            PluginSettingsState.State(
                enableWrapWithExpectedTypeIntention = true,
                enableWrapItemsWithExpectedTypeIntention = true,
                enableUnwrapToExpectedTypeIntention = true,
                enableUnwrapItemsToExpectedTypeIntention = true,
                enableIntroduceCustomTypeFromStdlibIntention = true,
                enablePopulateArgumentsIntention = true,
                enablePyMissingInDunderAllInspection = true,
                enableCopyPackageContentAction = true,
                enableRestoreSourceRootPrefix = true,
                enableRelativeImportPreference = true,
                enableDictAccessIntention = true,
                enableChangeVisibilityIntention = true,
                enableCallableToProtocolIntention = true,
                enableIntroduceParameterObjectIntention = true,
                enableDataclassMissingInspection = true,
                enablePrivateModuleImportInspection = true,
                enableCopyBuildNumberAction = true,
                enableCreateLocalVariableIntention = true,
                enableStructureViewPrivateMembersFilter = true,
                enableMakeParameterOptionalIntention = true,
                enableMakeParameterMandatoryIntention = true,
                enableAddExceptionCaptureIntention = true,
                enableWrapExceptionsWithParenthesesIntention = true,
                enableImplementAbstractMethodInChildClassesIntention = true,
                enableAbstractMethodNotImplementedInspection = true,
                enableConstantFinalInspection = true,
                enableShadowingStdlibModuleInspection = true,
                enableMakeMemberAbstractInAbstractClassIntention = true,
                enablePyReturnCompletionContributor = true,
                enablePyMockPatchReferenceContributor = true,
                enablePyFilterWarningsReferenceContributor = true,
                enableTypeAnnotationUsageFilteringRule = true,
                enablePyMessageConsoleFilter = true,
                enablePytestIdentifierSearchEverywhereContributor = true,
                enableIntroduceParameterObjectAction = true,
                enableIntroduceParameterObjectRefactoringAction = true,
                enableCopyBlockWithDependenciesAction = true,
                enableCopyPytestNodeIdsAction = true,
                enableCopyFQNsAction = true,
                enableCopyStacktraceAction = true,
                enableJumpToPytestNodeInTestTreeAction = true,
                enableTogglePytestSkipFromTestTreeAction = true,
                enableTogglePytestSkipIntention = true,
                enableParametrizePytestTestIntention = true,
                enableConvertPytestParamIntention = true,
                enableStripSignatureTypeAnnotationsIntention = true,
                suppressSuggestedRefactoringSignatureChangeIntention = true,
                preferOwnTypesInUnionWrapping = true,
                includeStdlibInUnionWrapping = true,
                enableConnexionInspections = true,
                enableConnexionCompletion = true,
                enableNewTypeTypeVarParamSpecRename = true,
                enableToggleTypeAliasIntention = true,
                enableExportSymbolToTargetIntention = true,
            ),
        )
        PythonTestSetup.createAndRegisterSdk(
            root = myFixture.tempDirFixture.getFile("/")!!,
            disposable = testRootDisposable
        )
        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
    }
}