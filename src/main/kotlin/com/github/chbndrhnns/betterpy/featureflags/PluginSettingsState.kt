package com.github.chbndrhnns.betterpy.featureflags

import com.github.chbndrhnns.betterpy.features.refactoring.parameterobject.ParameterObjectFeatureSettings
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger

/**
 * Persistent settings state for the plugin.
 * Settings are organized by feature category for easier navigation.
 * Each feature toggle is annotated with [Feature] to bind it to a YAML
 * declaration under src/main/resources/features.
 */
@State(name = "PycharmDddToolkitSettings", storages = [Storage("pycharm-ddd-toolkit.xml")])
class PluginSettingsState : PersistentStateComponent<PluginSettingsState.State> {
    data class State(
        // ---- Type Wrapping/Unwrapping Intentions ----
        @Feature("wrap-with-expected-type")
        var enableWrapWithExpectedTypeIntention: Boolean = FeatureDefaults.defaultEnabled("wrap-with-expected-type"),

        @Feature("wrap-items-with-expected-type")
        var enableWrapItemsWithExpectedTypeIntention: Boolean = FeatureDefaults.defaultEnabled("wrap-items-with-expected-type"),

        @Feature("unwrap-to-expected-type")
        var enableUnwrapToExpectedTypeIntention: Boolean = FeatureDefaults.defaultEnabled("unwrap-to-expected-type"),

        @Feature("unwrap-items-to-expected-type")
        var enableUnwrapItemsToExpectedTypeIntention: Boolean = FeatureDefaults.defaultEnabled("unwrap-items-to-expected-type"),

        // ---- Type Bucket Priority Settings ----
        @Feature("prefer-own-types-in-union-wrapping")
        var preferOwnTypesInUnionWrapping: Boolean = FeatureDefaults.defaultEnabled("prefer-own-types-in-union-wrapping"),
        @Feature("include-stdlib-in-union-wrapping")
        var includeStdlibInUnionWrapping: Boolean = FeatureDefaults.defaultEnabled("include-stdlib-in-union-wrapping"),

        // ---- Parameter & Argument Intentions ----
        @Feature("populate-arguments")
        var enablePopulateArgumentsIntention: Boolean = FeatureDefaults.defaultEnabled("populate-arguments"),

        @Feature("make-parameter-optional")
        var enableMakeParameterOptionalIntention: Boolean = FeatureDefaults.defaultEnabled("make-parameter-optional"),

        @Feature("make-parameter-mandatory")
        var enableMakeParameterMandatoryIntention: Boolean = FeatureDefaults.defaultEnabled("make-parameter-mandatory"),

        @Feature("create-local-variable")
        var enableCreateLocalVariableIntention: Boolean = FeatureDefaults.defaultEnabled("create-local-variable"),

        @Feature("assign-statement-to-variable")
        var enableAssignStatementToVariableIntention: Boolean = FeatureDefaults.defaultEnabled("assign-statement-to-variable"),

        @Feature("capture-unused-parameters")
        var enableCaptureUnusedParametersIntention: Boolean = FeatureDefaults.defaultEnabled("capture-unused-parameters"),

        // ---- Code Structure Intentions ----
        @Feature("dict-access")
        var enableDictAccessIntention: Boolean = FeatureDefaults.defaultEnabled("dict-access"),

        @Feature("convert-to-f-string")
        var enableConvertToFStringIntention: Boolean = FeatureDefaults.defaultEnabled("convert-to-f-string"),

        @Feature("change-visibility")
        var enableChangeVisibilityIntention: Boolean = FeatureDefaults.defaultEnabled("change-visibility"),

        @Feature("callable-to-protocol")
        var enableCallableToProtocolIntention: Boolean = FeatureDefaults.defaultEnabled("callable-to-protocol"),

        @Feature("add-exception-capture")
        var enableAddExceptionCaptureIntention: Boolean = FeatureDefaults.defaultEnabled("add-exception-capture"),

        @Feature("wrap-exceptions-with-parentheses")
        var enableWrapExceptionsWithParenthesesIntention: Boolean = FeatureDefaults.defaultEnabled("wrap-exceptions-with-parentheses"),

        @Feature("strip-signature-type-annotations")
        var enableStripSignatureTypeAnnotationsIntention: Boolean = FeatureDefaults.defaultEnabled("strip-signature-type-annotations"),

        @Feature("add-self-parameter")
        var enableAddSelfParameterIntention: Boolean = FeatureDefaults.defaultEnabled("add-self-parameter"),

        @Feature("copy-method-annotations-from-parent")
        var enableCopyMethodAnnotationsFromParentIntention: Boolean =
            FeatureDefaults.defaultEnabled("copy-method-annotations-from-parent"),

        // ---- Abstract Method Intentions ----
        @Feature("implement-abstract-method-in-child-classes")
        var enableImplementAbstractMethodInChildClassesIntention: Boolean = FeatureDefaults.defaultEnabled("implement-abstract-method-in-child-classes"),

        @Feature("make-member-abstract-in-abstract-class")
        var enableMakeMemberAbstractInAbstractClassIntention: Boolean = FeatureDefaults.defaultEnabled("make-member-abstract-in-abstract-class"),

        // ---- Pytest Intentions ----
        @Feature("advanced-pytest-fixtures")
        var enableAdvancedPytestFixtureFeatures: Boolean = FeatureDefaults.defaultEnabled("advanced-pytest-fixtures"),

        @Feature("create-pytest-fixtures-from-parameters")
        var enableCreatePytestFixtureFromParameter: Boolean = FeatureDefaults.defaultEnabled("create-pytest-fixtures-from-parameters"),

        @Feature("new-pytest-members")
        var enableNewPytestMemberActions: Boolean = FeatureDefaults.defaultEnabled("new-pytest-members"),

        @Feature("toggle-pytest-skip")
        var enableTogglePytestSkipIntention: Boolean = FeatureDefaults.defaultEnabled("toggle-pytest-skip"),

        @Feature("parametrize-pytest-test")
        var enableParametrizePytestTestIntention: Boolean = FeatureDefaults.defaultEnabled("parametrize-pytest-test"),

        @Feature("convert-pytest-param")
        var enableConvertPytestParamIntention: Boolean = FeatureDefaults.defaultEnabled("convert-pytest-param"),

        @Feature("surround-with-pytest-raises")
        var enableSurroundWithPytestRaisesIntention: Boolean = FeatureDefaults.defaultEnabled("surround-with-pytest-raises"),

        @Feature("extract-pytest-fixture")
        var enableExtractPytestFixtureRefactoring: Boolean = FeatureDefaults.defaultEnabled("extract-pytest-fixture"),

        @Feature("inline-pytest-fixture")
        var enableInlinePytestFixtureRefactoring: Boolean = FeatureDefaults.defaultEnabled("inline-pytest-fixture"),

        @Feature("mock-type-provider")
        var enablePyMockTypeProvider: Boolean = FeatureDefaults.defaultEnabled("mock-type-provider"),

        @Feature("wrap-test-in-class")
        var enableWrapTestInClassIntention: Boolean = FeatureDefaults.defaultEnabled("wrap-test-in-class"),

        @Feature("use-actual-test-outcome")
        var enableUseActualTestOutcomeIntention: Boolean = FeatureDefaults.defaultEnabled("use-actual-test-outcome"),

        // ---- Inspections ----
        @Feature("missing-in-dunder-all-inspection")
        var enablePyMissingInDunderAllInspection: Boolean = FeatureDefaults.defaultEnabled("missing-in-dunder-all-inspection"),

        @Feature("dataclass-missing-inspection")
        var enableDataclassMissingInspection: Boolean = FeatureDefaults.defaultEnabled("dataclass-missing-inspection"),

        @Feature("private-module-import-inspection")
        var enablePrivateModuleImportInspection: Boolean = FeatureDefaults.defaultEnabled("private-module-import-inspection"),

        @Feature("abstract-method-not-implemented-inspection")
        var enableAbstractMethodNotImplementedInspection: Boolean = FeatureDefaults.defaultEnabled("abstract-method-not-implemented-inspection"),

        @Feature("overridden-method-missing-type-annotations-inspection")
        var enableOverriddenMethodMissingTypeAnnotationsInspection: Boolean =
            FeatureDefaults.defaultEnabled("overridden-method-missing-type-annotations-inspection"),

        @Feature("constant-final-inspection")
        var enableConstantFinalInspection: Boolean = FeatureDefaults.defaultEnabled("constant-final-inspection"),

        @Feature("missing-f-string-prefix-inspection")
        var enableMissingFStringPrefixInspection: Boolean = FeatureDefaults.defaultEnabled("missing-f-string-prefix-inspection"),

        @Feature("shadowing-stdlib-module-inspection")
        var enableShadowingStdlibModuleInspection: Boolean = FeatureDefaults.defaultEnabled("shadowing-stdlib-module-inspection"),

        @Feature("unresolved-reference-as-error-inspection")
        var enableUnresolvedReferenceAsErrorInspection: Boolean = FeatureDefaults.defaultEnabled("unresolved-reference-as-error-inspection"),

        @Feature("mock-unresolved-reference-inspection")
        var enablePyMockUnresolvedReferenceInspection: Boolean = FeatureDefaults.defaultEnabled("mock-unresolved-reference-inspection"),

        @Feature("mock-return-assignment-inspection")
        var enablePyMockReturnAssignmentInspection: Boolean = FeatureDefaults.defaultEnabled("mock-return-assignment-inspection"),

        @Feature("mock-patch-object-attribute-inspection")
        var enablePyMockPatchObjectAttributeInspection: Boolean = FeatureDefaults.defaultEnabled("mock-patch-object-attribute-inspection"),

        @Feature("mock-patch-unresolved-reference-inspection")
        var enablePyMockPatchUnresolvedReferenceInspection: Boolean = FeatureDefaults.defaultEnabled("mock-patch-unresolved-reference-inspection"),

        // ---- Copy/Clipboard Actions ----
        @Feature("copy-package-content")
        var enableCopyPackageContentAction: Boolean = FeatureDefaults.defaultEnabled("copy-package-content"),

        @Feature("copy-build-number")
        var enableCopyBuildNumberAction: Boolean = FeatureDefaults.defaultEnabled("copy-build-number"),

        @Feature("copy-block-with-dependencies")
        var enableCopyBlockWithDependenciesAction: Boolean = FeatureDefaults.defaultEnabled("copy-block-with-dependencies"),

        @Feature("copy-pytest-node-ids")
        var enableCopyPytestNodeIdsAction: Boolean = FeatureDefaults.defaultEnabled("copy-pytest-node-ids"),

        @Feature("copy-pytest-node-id-from-editor")
        var enableCopyPytestNodeIdFromEditorAction: Boolean = FeatureDefaults.defaultEnabled("copy-pytest-node-id-from-editor"),

        @Feature("copy-fqns")
        var enableCopyFQNsAction: Boolean = FeatureDefaults.defaultEnabled("copy-fqns"),

        @Feature("copy-stacktrace")
        var enableCopyStacktraceAction: Boolean = FeatureDefaults.defaultEnabled("copy-stacktrace"),

        @Feature("copy-as-import-statement")
        var enableCopyAsImportStatementAction: Boolean = FeatureDefaults.defaultEnabled("copy-as-import-statement"),

        // ---- Refactoring Actions ----
        var parameterObject: ParameterObjectFeatureSettings =
            ParameterObjectFeatureSettings(),

        @Feature("introduce-custom-type")
        var enableIntroduceCustomTypeRefactoringAction: Boolean = FeatureDefaults.defaultEnabled("introduce-custom-type"),

        // ---- Pytest Tree Actions ----
        @Feature("jump-to-pytest-node-in-test-tree")
        var enableJumpToPytestNodeInTestTreeAction: Boolean = FeatureDefaults.defaultEnabled("jump-to-pytest-node-in-test-tree"),

        @Feature("toggle-pytest-skip-from-test-tree")
        var enableTogglePytestSkipFromTestTreeAction: Boolean = FeatureDefaults.defaultEnabled("toggle-pytest-skip-from-test-tree"),

        @Feature("run-with-debug-logging")
        var enableRunWithDebugLoggingFromTestTreeAction: Boolean = FeatureDefaults.defaultEnabled("run-with-debug-logging"),

        // ---- Completion & Reference Contributors ----
        @Feature("return-completion")
        var enablePyReturnCompletionContributor: Boolean = FeatureDefaults.defaultEnabled("return-completion"),

        @Feature("expected-type-completion")
        var enableExpectedTypeCompletionContributor: Boolean = FeatureDefaults.defaultEnabled("expected-type-completion"),

        @Feature("named-argument-from-scope-completion")
        var enableNamedArgumentFromScopeCompletionContributor: Boolean = FeatureDefaults.defaultEnabled("named-argument-from-scope-completion"),

        @Feature("mock-patch-object-attribute-completion")
        var enableMockPatchObjectAttributeCompletionContributor: Boolean = FeatureDefaults.defaultEnabled("mock-patch-object-attribute-completion"),

        @Feature("newtype-typevar-paramspec-reference")
        var enableNewTypeTypeVarParamSpecReferenceContributor: Boolean = FeatureDefaults.defaultEnabled("newtype-typevar-paramspec-reference"),

        @Feature("mock-patch-reference")
        var enablePyMockPatchReferenceContributor: Boolean = FeatureDefaults.defaultEnabled("mock-patch-reference"),

        @Feature("filter-warnings-reference")
        var enablePyFilterWarningsReferenceContributor: Boolean = FeatureDefaults.defaultEnabled("filter-warnings-reference"),

        @Feature("pytest-identifier-search-everywhere")
        var enablePytestIdentifierSearchEverywhereContributor: Boolean = FeatureDefaults.defaultEnabled("pytest-identifier-search-everywhere"),

        @Feature("toggle-type-alias")
        var enableToggleTypeAliasIntention: Boolean = FeatureDefaults.defaultEnabled("toggle-type-alias"),

        @Feature("export-symbol-to-target")
        var enableExportSymbolToTargetIntention: Boolean = FeatureDefaults.defaultEnabled("export-symbol-to-target"),

        @Feature("unexport-symbol")
        var enableUnexportSymbolIntention: Boolean = FeatureDefaults.defaultEnabled("unexport-symbol"),

        // ---- Import & Structure View Settings ----
        @Feature("enhanced-python-structure-view")
        var enableEnhancedPythonStructureView: Boolean = FeatureDefaults.defaultEnabled("enhanced-python-structure-view"),

        @Feature("restore-source-root-prefix")
        var enableRestoreSourceRootPrefix: Boolean = FeatureDefaults.defaultEnabled("restore-source-root-prefix"),

        @Feature("strict-source-root-import-inspection")
        var enableStrictSourceRootImportInspection: Boolean = FeatureDefaults.defaultEnabled("strict-source-root-import-inspection"),

        @Feature("relative-import-preference")
        var enableRelativeImportPreference: Boolean = FeatureDefaults.defaultEnabled("relative-import-preference"),

        @Feature("structure-view-private-members-filter")
        var enableStructureViewPrivateMembersFilter: Boolean = FeatureDefaults.defaultEnabled("structure-view-private-members-filter"),

        @Feature("hide-transient-imports")
        var enableHideTransientImports: Boolean = FeatureDefaults.defaultEnabled("hide-transient-imports"),

        // ---- Navigation Bar Settings ----
        @Feature("python-package-run-line-marker")
        var enablePyPackageRunLineMarkerContributor: Boolean = FeatureDefaults.defaultEnabled("python-package-run-line-marker"),

        @Feature("python-navigation-bar")
        var enablePythonNavigationBar: Boolean = FeatureDefaults.defaultEnabled("python-navigation-bar"),

        @Feature("py-goto-target-presentation")
        var enablePyGotoTargetPresentation: Boolean = FeatureDefaults.defaultEnabled("py-goto-target-presentation"),

        // ---- Filters & Suppressors ----
        @Feature("type-annotation-usage-filtering")
        var enableTypeAnnotationUsageFilteringRule: Boolean = FeatureDefaults.defaultEnabled("type-annotation-usage-filtering"),

        @Feature("test-usage-filtering")
        var enableTestUsageFilteringRule: Boolean = FeatureDefaults.defaultEnabled("test-usage-filtering"),

        @Feature("message-console-filter")
        var enablePyMessageConsoleFilter: Boolean = FeatureDefaults.defaultEnabled("message-console-filter"),

        @Feature("suppress-signature-change-intention")
        var suppressSuggestedRefactoringSignatureChangeIntention: Boolean = FeatureDefaults.defaultEnabled("suppress-signature-change-intention"),

        @Feature("rename-to-self-filter")
        var enableRenameToSelfFilter: Boolean = FeatureDefaults.defaultEnabled("rename-to-self-filter"),

        // ---- Connexion ----
        @Feature("connexion-line-markers")
        var enableConnexionLineMarkers: Boolean = FeatureDefaults.defaultEnabled("connexion-line-markers"),

        @Feature("connexion-inspections")
        var enableConnexionInspections: Boolean = FeatureDefaults.defaultEnabled("connexion-inspections"),

        @Feature("connexion-completion")
        var enableConnexionCompletion: Boolean = FeatureDefaults.defaultEnabled("connexion-completion"),

        @Feature("newtype-typevar-paramspec-rename")
        var enableNewTypeTypeVarParamSpecRename: Boolean = FeatureDefaults.defaultEnabled("newtype-typevar-paramspec-rename"),

        // ---- Documentation ----
        @Feature("enhanced-dataclass-quick-info")
        var enableEnhancedDataclassQuickInfo: Boolean = FeatureDefaults.defaultEnabled("enhanced-dataclass-quick-info"),

        @Feature("package-run-configuration")
        var enablePyPackageRunConfigurationAction: Boolean = FeatureDefaults.defaultEnabled("package-run-configuration"),

        @Feature("pytest-failed-line-inspection")
        var enablePyTestFailedLineInspection: Boolean = FeatureDefaults.defaultEnabled("pytest-failed-line-inspection"),

        @Feature("pytest-fixture-scope-inspection")
        var enablePytestFixtureScopeInspection: Boolean = FeatureDefaults.defaultEnabled("pytest-fixture-scope-inspection"),

        @Feature("pytest-fixture-uninjected-reference-inspection")
        var enablePytestFixtureUninjectedReferenceInspection: Boolean = FeatureDefaults.defaultEnabled("pytest-fixture-uninjected-reference-inspection"),

        @Feature("pytest-fixture-to-usefixtures-inspection")
        var enablePytestFixtureToUsefixturesInspection: Boolean = FeatureDefaults.defaultEnabled("pytest-fixture-to-usefixtures-inspection"),

        @Feature("pytest-parametrize-count-mismatch-inspection")
        var enablePytestParametrizeCountMismatchInspection: Boolean = FeatureDefaults.defaultEnabled("pytest-parametrize-count-mismatch-inspection"),

        @Feature("pytest-parametrize-ids-count-inspection")
        var enablePytestParametrizeIdsCountInspection: Boolean = FeatureDefaults.defaultEnabled("pytest-parametrize-ids-count-inspection"),

        /**
         * Snapshotted state before "Mute All" was activated.
         * If non-null, it means the plugin is currently in "Mute All" mode.
         */
        var originalState: State? = null,

        /**
         * Snapshotted state before the incubating feature toggle was activated.
         * If non-null, it means incubating features are temporarily toggled.
         */
        var incubatingState: State? = null
    )

    private var myState = State()
    private var incubatingBackup: Map<String, Boolean>? = null

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        // Clear any persisted incubating snapshot (it's a runtime-only override)
        myState.incubatingState?.let {
            myState = it
        }
        if (isMuted()) {
            unmute()
        }
        FeatureRegistry.instance().disableUnavailableFeatures()
    }

    fun isMuted(): Boolean = myState.originalState != null

    fun isIncubatingOverrideActive(): Boolean = incubatingBackup != null

    fun mute() {
        if (isMuted()) return

        // Save current state
        val backup = myState.copy(originalState = null)
        myState.originalState = backup

        // Disable all features
        val fields = State::class.java.declaredFields
        for (field in fields) {
            var hasAnnotation = field.isAnnotationPresent(Feature::class.java)
            if (!hasAnnotation) {
                // Check getter (PROPERTY target often puts annotation on getter)
                val capitalized = field.name.replaceFirstChar { it.uppercase() }
                
                // 1. Check standard getter
                val getterNames = listOf("get$capitalized", "is$capitalized")
                for (name in getterNames) {
                    try {
                        val method = State::class.java.getDeclaredMethod(name)
                        if (method.isAnnotationPresent(Feature::class.java)) {
                            hasAnnotation = true
                            break
                        }
                    } catch (e: NoSuchMethodException) {
                        // ignore
                    }
                }

                // 2. Check Kotlin synthetic annotations method (e.g. getProperty$annotations)
                if (!hasAnnotation) {
                     val annotationsMethodName = "get$capitalized\$annotations"
                     try {
                        val method = State::class.java.getDeclaredMethod(annotationsMethodName)
                        if (method.isAnnotationPresent(Feature::class.java)) {
                            hasAnnotation = true
                        }
                     } catch (e: NoSuchMethodException) {
                        // ignore
                     }
                }
            }

            if (hasAnnotation) {
                field.isAccessible = true
                if (field.type == Boolean::class.javaPrimitiveType || field.type == Boolean::class.javaObjectType) {
                    try {
                        field.set(myState, false)
                    } catch (e: Exception) {
                        // Best effort
                    }
                }
            }
        }
    }

    fun unmute() {
        if (!isMuted()) return
        myState.originalState?.let {
            myState = it
        }
    }

    fun toggleIncubatingFeatures() {
        if (isIncubatingOverrideActive()) {
            restoreIncubatingState()
            LOG.info("Incubating feature overrides restored.")
            return
        }

        val registry = FeatureRegistry.instance()
        val features = registry.getIncubatingFeatures()
        incubatingBackup = features.associate { it.id to it.isEnabled() }

        features.forEach { feature ->
            feature.setEnabled(false)
        }
        LOG.info("Incubating feature overrides activated.")
    }

    private fun restoreIncubatingState() {
        val backup = incubatingBackup ?: return
        val registry = FeatureRegistry.instance()
        backup.forEach { (id, enabled) ->
            registry.setFeatureEnabled(id, enabled)
        }
        incubatingBackup = null
    }

    companion object {
        private val LOG = Logger.getInstance(PluginSettingsState::class.java)

        @JvmStatic
        fun instance(): PluginSettingsState {
            return serviceOrNull<PluginSettingsState>() ?: run {
                LOG.warn("PluginSettingsState service unavailable; using default state")
                PluginSettingsState()
            }
        }
    }
}
