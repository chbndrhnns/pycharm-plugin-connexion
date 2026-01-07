package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureCheckboxBuilder.featureRow
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel

/**
 * Settings configurable for inspection-related features.
 * Uses [FeatureRegistry] to dynamically build the UI with maturity indicators and YouTrack links.
 * Includes a maturity filter panel at the top to filter features by status.
 */
class InspectionsConfigurable : BoundConfigurable("Inspections"), Configurable.WithEpDependencies {
    private val registry = FeatureRegistry.instance()

    override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
        return listOf(ExtensionPointName.create<Any>("com.intellij.localInspection"))
    }

    override fun createPanel(): DialogPanel {
        return createFilterableFeaturePanel { visibleMaturities, searchTerm ->
            // All inspections are in the INSPECTIONS category
            group("Code Quality Inspections") {
                registry.getFeature("missing-in-dunder-all-inspection")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("dataclass-missing-inspection")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("private-module-import-inspection")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("abstract-method-not-implemented-inspection")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("constant-final-inspection")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("shadowing-stdlib-module-inspection")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
                registry.getFeature("unresolved-reference-as-error-inspection")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
            }

            group("Test Inspections") {
                registry.getFeature("pytest-failed-line-inspection")
                    ?.let { featureRow(it, visibleMaturities = visibleMaturities, searchTerm = searchTerm) }
            }
        }.asDialogPanel()
    }
}
