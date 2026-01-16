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
                enableIntroduceCustomTypeRefactoringAction = true,
                enablePopulateArgumentsIntention = true,
                enablePyMissingInDunderAllInspection = true,
                enableCopyPackageContentAction = true,
                enableRestoreSourceRootPrefix = true,
                enableStrictSourceRootImportInspection = true,
                enableRelativeImportPreference = true,
                enableDictAccessIntention = true,
                enableChangeVisibilityIntention = true,
                enableCallableToProtocolIntention = true,
                enableDataclassMissingInspection = true,
                enablePrivateModuleImportInspection = true,
                enableCopyBuildNumberAction = true,
                enableParameterObjectRefactoring = true,
                enableParameterObjectGutterIcon = true,
                enableCreateLocalVariableIntention = true,
                enableStructureViewPrivateMembersFilter = true,
                enableHideTransientImports = true,
                enableMakeParameterOptionalIntention = true,
                enableMakeParameterMandatoryIntention = true,
                enableAddExceptionCaptureIntention = true,
                enableWrapExceptionsWithParenthesesIntention = true,
                enableImplementAbstractMethodInChildClassesIntention = true,
                enableAbstractMethodNotImplementedInspection = true,
                enableConstantFinalInspection = true,
                enableShadowingStdlibModuleInspection = true,
                enableUnresolvedReferenceAsErrorInspection = true,
                enableMakeMemberAbstractInAbstractClassIntention = true,
                enablePyReturnCompletionContributor = true,
                enablePyMockPatchReferenceContributor = true,
                enablePyFilterWarningsReferenceContributor = true,
                enableTypeAnnotationUsageFilteringRule = true,
                enablePyMessageConsoleFilter = true,
                enablePytestIdentifierSearchEverywhereContributor = true,
                enableCopyBlockWithDependenciesAction = true,
                enableCopyPytestNodeIdsAction = true,
                enableCopyPytestNodeIdFromEditorAction = true,
                enableCopyFQNsAction = true,
                enableCopyStacktraceAction = true,
                enableJumpToPytestNodeInTestTreeAction = true,
                enableTogglePytestSkipFromTestTreeAction = true,
                enableTogglePytestSkipIntention = true,
                enableParametrizePytestTestIntention = true,
                enableConvertPytestParamIntention = true,
                enablePyMockTypeProvider = true,
                enableWrapTestInClassIntention = true,
                enableStripSignatureTypeAnnotationsIntention = true,
                suppressSuggestedRefactoringSignatureChangeIntention = true,
                preferOwnTypesInUnionWrapping = true,
                includeStdlibInUnionWrapping = true,
                enableConnexionInspections = true,
                enableConnexionCompletion = true,
                enableNewTypeTypeVarParamSpecRename = true,
                enableToggleTypeAliasIntention = true,
                enableExportSymbolToTargetIntention = true,
                enableUnexportSymbolIntention = true,
                enablePyPackageRunConfigurationAction = true,
                enablePyTestFailedLineInspection = true,
                enableRenameToSelfFilter = true,
                enablePyGotoTargetPresentation = true,
                enablePythonNavigationBar = true,
                enableEnhancedDataclassQuickInfo = true,
                defaultParameterObjectBaseType = "dataclass",
            ),
        )
        PythonTestSetup.createAndRegisterSdk(
            root = myFixture.tempDirFixture.getFile("/")!!,
            disposable = testRootDisposable
        )
        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
    }
}