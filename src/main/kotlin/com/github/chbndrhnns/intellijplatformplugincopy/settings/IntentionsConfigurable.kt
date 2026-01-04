package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel

/**
 * Settings configurable for intention-related features.
 * Uses [FeatureRegistry] to dynamically build the UI with maturity indicators and YouTrack links.
 * Includes a maturity filter panel at the top to filter features by status.
 */
class IntentionsConfigurable : BoundConfigurable("Intentions"), Configurable.WithEpDependencies {
    private val registry = FeatureRegistry.instance()

    override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
        return emptyList()
    }

    override fun createPanel(): DialogPanel {
        return createFilterableFeaturePanel { visibleMaturities, searchTerm ->
            // Type Wrapping/Unwrapping
            group("Type Wrapping/Unwrapping") {
                registry.getFeature("wrap-with-expected-type")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("wrap-items-with-expected-type")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("unwrap-to-expected-type")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("unwrap-items-to-expected-type")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("introduce-custom-type-from-stdlib")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
            }

            // Parameter & Argument
            group("Parameter & Argument") {
                registry.getFeature("populate-arguments")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("make-parameter-optional")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("make-parameter-mandatory")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("create-local-variable")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
            }

            // Code Structure
            group("Code Structure") {
                registry.getFeature("dict-access")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("change-visibility")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("callable-to-protocol")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("add-exception-capture")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("wrap-exceptions-with-parentheses")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("strip-signature-type-annotations")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("toggle-type-alias")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("newtype-typevar-paramspec-rename")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
            }

            // Abstract Methods
            group("Abstract Methods") {
                registry.getFeature("implement-abstract-method-in-child-classes")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("make-member-abstract-in-abstract-class")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
            }

            // Pytest
            group("Pytest") {
                registry.getFeature("toggle-pytest-skip")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("parametrize-pytest-test")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("convert-pytest-param")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("mock-type-provider")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("wrap-test-in-class")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
            }

            // Filters & Suppressors
            group("Filters & Suppressors") {
                registry.getFeature("suppress-signature-change-intention")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("rename-to-self-filter")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
            }

            // Other
            group("Other") {
                registry.getFeature("package-run-configuration")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
            }
        }.asDialogPanel()
    }
}
