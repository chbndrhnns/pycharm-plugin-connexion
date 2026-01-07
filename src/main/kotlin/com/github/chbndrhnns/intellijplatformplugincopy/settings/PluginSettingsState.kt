package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Persistent settings state for the plugin.
 * Settings are organized by feature category for easier navigation.
 * Each feature toggle is annotated with [Feature] metadata for declarative
 * feature management, maturity tracking, and YouTrack issue linking.
 */
@State(name = "PycharmDddToolkitSettings", storages = [Storage("pycharm-ddd-toolkit.xml")])
class PluginSettingsState : PersistentStateComponent<PluginSettingsState.State> {
    data class State(
        // ---- Type Wrapping/Unwrapping Intentions ----
        @Feature(
            id = "wrap-with-expected-type",
            displayName = "Wrap with expected type",
            description = "Wraps an expression with a type that matches the expected type annotation",
            category = FeatureCategory.TYPE_WRAPPING
        )
        var enableWrapWithExpectedTypeIntention: Boolean = true,

        @Feature(
            id = "wrap-items-with-expected-type",
            displayName = "Wrap items with expected type",
            description = "Wraps collection items with types matching the expected type annotation",
            category = FeatureCategory.TYPE_WRAPPING
        )
        var enableWrapItemsWithExpectedTypeIntention: Boolean = true,

        @Feature(
            id = "unwrap-to-expected-type",
            displayName = "Unwrap to expected type",
            description = "Unwraps an expression to match the expected type annotation",
            category = FeatureCategory.TYPE_WRAPPING
        )
        var enableUnwrapToExpectedTypeIntention: Boolean = true,

        @Feature(
            id = "unwrap-items-to-expected-type",
            displayName = "Unwrap items to expected type",
            description = "Unwraps collection items to match the expected type annotation",
            category = FeatureCategory.TYPE_WRAPPING
        )
        var enableUnwrapItemsToExpectedTypeIntention: Boolean = true,

        // ---- Type Bucket Priority Settings ----
        /** When true, always prefer OWN (project) types over STDLIB types in union wrapping. */
        var preferOwnTypesInUnionWrapping: Boolean = true,
        /** When true, include STDLIB types as candidates in union wrapping (otherwise filter them out). */
        var includeStdlibInUnionWrapping: Boolean = true,

        // ---- Parameter & Argument Intentions ----
        @Feature(
            id = "populate-arguments",
            displayName = "Populate arguments",
            description = "Automatically fills in function call arguments based on the function signature",
            category = FeatureCategory.ARGUMENTS
        )
        var enablePopulateArgumentsIntention: Boolean = true,

        @Feature(
            id = "make-parameter-optional",
            displayName = "Make parameter optional",
            description = "Converts a required parameter to an optional one with a default value",
            category = FeatureCategory.ARGUMENTS
        )
        var enableMakeParameterOptionalIntention: Boolean = true,

        @Feature(
            id = "make-parameter-mandatory",
            displayName = "Make parameter mandatory",
            description = "Converts an optional parameter to a required one by removing the default value",
            category = FeatureCategory.ARGUMENTS
        )
        var enableMakeParameterMandatoryIntention: Boolean = true,

        @Feature(
            id = "create-local-variable",
            displayName = "Create local variable",
            description = "Creates a local variable from an expression",
            category = FeatureCategory.ARGUMENTS
        )
        var enableCreateLocalVariableIntention: Boolean = true,

        // ---- Code Structure Intentions ----
        @Feature(
            id = "dict-access",
            displayName = "Dict access conversion",
            description = "Converts between dict bracket access and .get() method calls",
            category = FeatureCategory.CODE_STRUCTURE
        )
        var enableDictAccessIntention: Boolean = true,

        @Feature(
            id = "change-visibility",
            displayName = "Change visibility",
            description = "Changes the visibility of a symbol (public/protected/private)",
            category = FeatureCategory.CODE_STRUCTURE
        )
        var enableChangeVisibilityIntention: Boolean = true,

        @Feature(
            id = "callable-to-protocol",
            displayName = "Convert Callable to Protocol",
            description = "Converts a Callable type hint to an equivalent Protocol definition",
            category = FeatureCategory.CODE_STRUCTURE,
            maturity = FeatureMaturity.INCUBATING
        )
        var enableCallableToProtocolIntention: Boolean = true,

        @Feature(
            id = "add-exception-capture",
            displayName = "Add exception capture",
            description = "Adds an 'as' clause to capture the exception in an except block",
            category = FeatureCategory.CODE_STRUCTURE
        )
        var enableAddExceptionCaptureIntention: Boolean = true,

        @Feature(
            id = "wrap-exceptions-with-parentheses",
            displayName = "Wrap exceptions with parentheses",
            description = "Wraps multiple exception types in parentheses for proper syntax",
            category = FeatureCategory.CODE_STRUCTURE
        )
        var enableWrapExceptionsWithParenthesesIntention: Boolean = true,

        @Feature(
            id = "strip-signature-type-annotations",
            displayName = "Strip signature type annotations",
            description = "Removes type annotations from a function signature",
            category = FeatureCategory.CODE_STRUCTURE
        )
        var enableStripSignatureTypeAnnotationsIntention: Boolean = true,

        // ---- Abstract Method Intentions ----
        @Feature(
            id = "implement-abstract-method-in-child-classes",
            displayName = "Implement abstract method in child classes",
            description = "Generates implementations of an abstract method in all child classes",
            category = FeatureCategory.ABSTRACT_METHODS
        )
        var enableImplementAbstractMethodInChildClassesIntention: Boolean = true,

        @Feature(
            id = "make-member-abstract-in-abstract-class",
            displayName = "Make member abstract",
            description = "Converts a concrete method to an abstract method in an abstract class",
            category = FeatureCategory.ABSTRACT_METHODS
        )
        var enableMakeMemberAbstractInAbstractClassIntention: Boolean = true,

        // ---- Pytest Intentions ----
        @Feature(
            id = "toggle-pytest-skip",
            displayName = "Toggle pytest skip",
            description = "Adds or removes @pytest.mark.skip decorator from a test",
            category = FeatureCategory.PYTEST
        )
        var enableTogglePytestSkipIntention: Boolean = true,

        @Feature(
            id = "parametrize-pytest-test",
            displayName = "Parametrize pytest test",
            description = "Converts a test function to use @pytest.mark.parametrize",
            category = FeatureCategory.PYTEST
        )
        var enableParametrizePytestTestIntention: Boolean = true,

        @Feature(
            id = "convert-pytest-param",
            displayName = "Convert pytest.param",
            description = "Converts between different pytest parameter formats",
            category = FeatureCategory.PYTEST
        )
        var enableConvertPytestParamIntention: Boolean = true,

        @Feature(
            id = "mock-type-provider",
            displayName = "Mock type provider",
            description = "Provides type information for unittest.mock objects",
            category = FeatureCategory.PYTEST
        )
        var enablePyMockTypeProvider: Boolean = true,

        @Feature(
            id = "wrap-test-in-class",
            displayName = "Wrap test in class",
            description = "Wraps a test function in a test class",
            category = FeatureCategory.PYTEST
        )
        var enableWrapTestInClassIntention: Boolean = true,

        // ---- Inspections ----
        @Feature(
            id = "missing-in-dunder-all-inspection",
            displayName = "Missing in __all__ inspection",
            description = "Reports public symbols that are not listed in __all__",
            category = FeatureCategory.INSPECTIONS
        )
        var enablePyMissingInDunderAllInspection: Boolean = true,

        @Feature(
            id = "dataclass-missing-inspection",
            displayName = "Dataclass missing inspection",
            description = "Reports missing dataclass decorator or field definitions",
            category = FeatureCategory.INSPECTIONS
        )
        var enableDataclassMissingInspection: Boolean = true,

        @Feature(
            id = "private-module-import-inspection",
            displayName = "Private module import inspection",
            description = "Reports imports from private modules (starting with underscore)",
            category = FeatureCategory.INSPECTIONS
        )
        var enablePrivateModuleImportInspection: Boolean = true,

        @Feature(
            id = "abstract-method-not-implemented-inspection",
            displayName = "Abstract method not implemented inspection",
            description = "Reports abstract methods that are not implemented in concrete subclasses",
            category = FeatureCategory.INSPECTIONS
        )
        var enableAbstractMethodNotImplementedInspection: Boolean = true,

        @Feature(
            id = "constant-final-inspection",
            displayName = "Constant should be Final inspection",
            description = "Reports module-level constants that should be annotated with Final",
            category = FeatureCategory.INSPECTIONS
        )
        var enableConstantFinalInspection: Boolean = true,

        @Feature(
            id = "shadowing-stdlib-module-inspection",
            displayName = "Shadowing stdlib module inspection",
            description = "Reports project modules that shadow Python standard library modules",
            category = FeatureCategory.INSPECTIONS
        )
        var enableShadowingStdlibModuleInspection: Boolean = true,

        @Feature(
            id = "unresolved-reference-as-error-inspection",
            displayName = "Unresolved reference as error",
            description = "Reports unresolved references as errors instead of warnings",
            category = FeatureCategory.INSPECTIONS
        )
        var enableUnresolvedReferenceAsErrorInspection: Boolean = true,

        // ---- Copy/Clipboard Actions ----
        @Feature(
            id = "copy-package-content",
            displayName = "Copy package content",
            description = "Copies the content of a Python package to the clipboard",
            category = FeatureCategory.ACTIONS
        )
        var enableCopyPackageContentAction: Boolean = true,

        @Feature(
            id = "copy-build-number",
            displayName = "Copy build number",
            description = "Copies the IDE build number to the clipboard",
            category = FeatureCategory.ACTIONS
        )
        var enableCopyBuildNumberAction: Boolean = true,

        @Feature(
            id = "copy-block-with-dependencies",
            displayName = "Copy block with dependencies",
            description = "Copies a code block along with its required imports and dependencies",
            category = FeatureCategory.ACTIONS
        )
        var enableCopyBlockWithDependenciesAction: Boolean = true,

        @Feature(
            id = "copy-pytest-node-ids",
            displayName = "Copy pytest node IDs",
            description = "Copies pytest node IDs for selected tests to the clipboard",
            category = FeatureCategory.ACTIONS
        )
        var enableCopyPytestNodeIdsAction: Boolean = true,

        @Feature(
            id = "copy-pytest-node-id-from-editor",
            displayName = "Copy pytest node ID from editor",
            description = "Copies the pytest node ID for the test at the caret position",
            category = FeatureCategory.ACTIONS
        )
        var enableCopyPytestNodeIdFromEditorAction: Boolean = true,

        @Feature(
            id = "copy-fqns",
            displayName = "Copy FQNs",
            description = "Copies fully qualified names of selected symbols to the clipboard",
            category = FeatureCategory.ACTIONS
        )
        var enableCopyFQNsAction: Boolean = true,

        @Feature(
            id = "copy-stacktrace",
            displayName = "Copy stacktrace",
            description = "Copies a formatted stacktrace to the clipboard",
            category = FeatureCategory.ACTIONS
        )
        var enableCopyStacktraceAction: Boolean = true,

        // ---- Refactoring Actions ----
        @Feature(
            id = "parameter-object-refactoring",
            displayName = "Parameter object refactoring",
            description = "Enables introduce/inline parameter object refactoring actions",
            category = FeatureCategory.ACTIONS
        )
        var enableParameterObjectRefactoring: Boolean = true,

        @Feature(
            id = "introduce-custom-type",
            displayName = "Introduce custom type",
            description = "Creates a custom type from a stdlib type annotation",
            category = FeatureCategory.ACTIONS
        )
        var enableIntroduceCustomTypeRefactoringAction: Boolean = true,

        // ---- Parameter Object Settings ----
        @Feature(
            id = "parameter-object-gutter-icon",
            displayName = "Parameter object gutter icon",
            description = "Shows a gutter icon on functions that are candidates for 'Introduce Parameter Object' refactoring",
            category = FeatureCategory.ACTIONS
        )
        var enableParameterObjectGutterIcon: Boolean = true,

        /** Default base type for parameter objects (dataclass, NamedTuple, TypedDict, pydantic.BaseModel) */
        var defaultParameterObjectBaseType: String = "dataclass",

        // ---- Pytest Tree Actions ----
        @Feature(
            id = "jump-to-pytest-node-in-test-tree",
            displayName = "Jump to pytest node in test tree",
            description = "Navigates to the corresponding node in the test tree view",
            category = FeatureCategory.PYTEST
        )
        var enableJumpToPytestNodeInTestTreeAction: Boolean = true,

        @Feature(
            id = "toggle-pytest-skip-from-test-tree",
            displayName = "Toggle pytest skip from test tree",
            description = "Adds or removes @pytest.mark.skip from the test tree context menu",
            category = FeatureCategory.PYTEST
        )
        var enableTogglePytestSkipFromTestTreeAction: Boolean = true,

        // ---- Completion & Reference Contributors ----
        @Feature(
            id = "return-completion",
            displayName = "Return completion",
            description = "Provides completion suggestions for return statements based on return type",
            category = FeatureCategory.COMPLETION
        )
        var enablePyReturnCompletionContributor: Boolean = true,

        @Feature(
            id = "mock-patch-reference",
            displayName = "Mock patch reference",
            description = "Provides reference resolution for mock.patch target strings",
            category = FeatureCategory.COMPLETION
        )
        var enablePyMockPatchReferenceContributor: Boolean = true,

        @Feature(
            id = "filter-warnings-reference",
            displayName = "Filter warnings reference",
            description = "Provides reference resolution for filterwarnings category strings",
            category = FeatureCategory.COMPLETION
        )
        var enablePyFilterWarningsReferenceContributor: Boolean = true,

        @Feature(
            id = "pytest-identifier-search-everywhere",
            displayName = "Pytest identifier in Search Everywhere",
            description = "Includes pytest identifiers in Search Everywhere results",
            category = FeatureCategory.COMPLETION
        )
        var enablePytestIdentifierSearchEverywhereContributor: Boolean = true,

        @Feature(
            id = "toggle-type-alias",
            displayName = "Toggle type alias",
            description = "Converts between TypeAlias and type statement syntax",
            category = FeatureCategory.CODE_STRUCTURE
        )
        var enableToggleTypeAliasIntention: Boolean = true,

        @Feature(
            id = "export-symbol-to-target",
            displayName = "Export symbol to target",
            description = "Exports a symbol to a specified target module's __all__",
            category = FeatureCategory.IMPORTS
        )
        var enableExportSymbolToTargetIntention: Boolean = true,

        @Feature(
            id = "unexport-symbol",
            displayName = "Unexport symbol",
            description = "Removes a symbol from __all__ and its corresponding import",
            category = FeatureCategory.IMPORTS
        )
        var enableUnexportSymbolIntention: Boolean = true,

        // ---- Import & Structure View Settings ----
        @Feature(
            id = "restore-source-root-prefix",
            displayName = "Restore source root prefix",
            description = "Restores the source root prefix in import statements",
            category = FeatureCategory.IMPORTS
        )
        var enableRestoreSourceRootPrefix: Boolean = true,

        @Feature(
            id = "strict-source-root-import-inspection",
            displayName = "Strict source root import inspection",
            description = "Reports imports that are missing the source root prefix",
            category = FeatureCategory.IMPORTS
        )
        var enableStrictSourceRootImportInspection: Boolean = true,

        @Feature(
            id = "relative-import-preference",
            displayName = "Relative import preference",
            description = "Prefers relative imports over absolute imports when applicable",
            category = FeatureCategory.IMPORTS
        )
        var enableRelativeImportPreference: Boolean = true,

        @Feature(
            id = "structure-view-private-members-filter",
            displayName = "Structure view private members filter",
            description = "Adds a filter to hide private members in the structure view",
            category = FeatureCategory.IMPORTS
        )
        var enableStructureViewPrivateMembersFilter: Boolean = true,

        @Feature(
            id = "hide-transient-imports",
            displayName = "Hide transient imports",
            description = "Hides transient (re-exported) imports in the structure view",
            category = FeatureCategory.IMPORTS
        )
        var enableHideTransientImports: Boolean = true,

        // ---- Navigation Bar Settings ----
        @Feature(
            id = "python-navigation-bar",
            displayName = "Python navigation bar",
            description = "Enables enhanced Python navigation bar with module path display",
            category = FeatureCategory.NAVIGATION,
            youtrackIssues = [
                "PY-53757",
            ]
        )
        var enablePythonNavigationBar: Boolean = true,

        @Feature(
            id = "py-goto-target-presentation",
            displayName = "Enhanced Go to Implementation presentation",
            description = "Prepends class name to method name in Go to Implementation popup when names are identical",
            category = FeatureCategory.NAVIGATION
        )
        var enablePyGotoTargetPresentation: Boolean = true,

        // ---- Filters & Suppressors ----
        @Feature(
            id = "type-annotation-usage-filtering",
            displayName = "Type annotation usage filtering",
            description = "Filters out type annotation usages from Find Usages results",
            category = FeatureCategory.FILTERS
        )
        var enableTypeAnnotationUsageFilteringRule: Boolean = true,

        @Feature(
            id = "message-console-filter",
            displayName = "Message console filter",
            description = "Filters and formats Python messages in the console",
            category = FeatureCategory.FILTERS
        )
        var enablePyMessageConsoleFilter: Boolean = true,

        @Feature(
            id = "suppress-signature-change-intention",
            displayName = "Suppress signature change intention",
            description = "Suppresses the suggested refactoring signature change intention",
            category = FeatureCategory.FILTERS
        )
        var suppressSuggestedRefactoringSignatureChangeIntention: Boolean = true,

        @Feature(
            id = "rename-to-self-filter",
            displayName = "Rename to self filter",
            description = "Filters out 'rename to self' suggestions in certain contexts",
            category = FeatureCategory.FILTERS
        )
        var enableRenameToSelfFilter: Boolean = true,

        // ---- Connexion ----
        @Feature(
            id = "connexion-inspections",
            displayName = "Connexion inspections",
            description = "Enables inspections for Connexion API framework",
            category = FeatureCategory.CONNEXION
        )
        var enableConnexionInspections: Boolean = true,

        @Feature(
            id = "connexion-completion",
            displayName = "Connexion completion",
            description = "Enables code completion for Connexion API framework",
            category = FeatureCategory.CONNEXION
        )
        var enableConnexionCompletion: Boolean = true,

        @Feature(
            id = "newtype-typevar-paramspec-rename",
            displayName = "NewType/TypeVar/ParamSpec rename",
            description = "Enables smart rename for NewType, TypeVar, and ParamSpec definitions",
            category = FeatureCategory.CODE_STRUCTURE
        )
        var enableNewTypeTypeVarParamSpecRename: Boolean = true,

        @Feature(
            id = "package-run-configuration",
            displayName = "Package run configuration",
            description = "Enables run configuration for Python packages with -m flag",
            category = FeatureCategory.ACTIONS
        )
        var enablePyPackageRunConfigurationAction: Boolean = true,

        @Feature(
            id = "pytest-failed-line-inspection",
            displayName = "Pytest failed line inspection",
            description = "Marks the line where a pytest test failed",
            category = FeatureCategory.INSPECTIONS
        )
        var enablePyTestFailedLineInspection: Boolean = true
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
