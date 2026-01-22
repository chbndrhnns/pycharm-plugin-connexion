package com.github.chbndrhnns.intellijplatformplugincopy.featureflags

/**
 * Generates documentation for plugin features from their metadata.
 * Can be used to generate markdown documentation, HTML, or other formats.
 */
object FeatureDocumentationGenerator {

    /**
     * Generates a complete markdown documentation of all features.
     *
     * @param includeHidden Whether to include hidden features in the documentation
     * @return Markdown-formatted documentation string
     */
    fun generateMarkdown(includeHidden: Boolean = false): String {
        val registry = FeatureRegistry.instance()
        val features = if (includeHidden) {
            registry.getAllFeatures()
        } else {
            registry.getVisibleFeatures()
        }

        return buildString {
            appendLine("# BetterPy Feature Reference")
            appendLine()
            appendLine("This document lists all features available in the BetterPy plugin.")
            appendLine()

            // Summary statistics
            val stableCount = features.count { it.maturity == FeatureMaturity.STABLE }
            val incubatingCount = features.count { it.maturity == FeatureMaturity.INCUBATING }
            val deprecatedCount = features.count { it.maturity == FeatureMaturity.DEPRECATED }
            val hiddenCount = if (includeHidden) features.count { it.maturity == FeatureMaturity.HIDDEN } else 0

            appendLine("## Summary")
            appendLine()
            appendLine("| Status | Count |")
            appendLine("|--------|-------|")
            appendLine("| Stable | $stableCount |")
            appendLine("| Incubating | $incubatingCount |")
            appendLine("| Deprecated | $deprecatedCount |")
            if (includeHidden) {
                appendLine("| Hidden | $hiddenCount |")
            }
            appendLine("| **Total** | **${features.size}** |")
            appendLine()

            // Features by category
            FeatureCategory.entries.forEach { category ->
                val categoryFeatures = features
                    .filter { it.category == category }
                    .sortedBy { it.displayName }

                if (categoryFeatures.isNotEmpty()) {
                    appendLine("## ${category.displayName}")
                    appendLine()

                    categoryFeatures.forEach { feature ->
                        appendLine("### ${feature.displayName}")
                        appendLine()

                        // Maturity badge
                        when (feature.maturity) {
                            FeatureMaturity.INCUBATING -> appendLine("🧪 **Status:** Incubating")
                            FeatureMaturity.DEPRECATED -> {
                                val removeInfo = if (feature.removeIn.isNotEmpty()) {
                                    " (will be removed in ${feature.removeIn})"
                                } else ""
                                appendLine("⚠️ **Status:** Deprecated$removeInfo")
                            }
                            FeatureMaturity.HIDDEN -> appendLine("👁️ **Status:** Hidden")
                            FeatureMaturity.STABLE -> {} // No badge for stable
                        }

                        // Description
                        if (feature.description.isNotEmpty()) {
                            appendLine()
                            appendLine(feature.description)
                        }

                        // Metadata table
                        appendLine()
                        appendLine("| Property | Value |")
                        appendLine("|----------|-------|")
                        appendLine("| ID | `${feature.id}` |")
                        appendLine("| Settings Key | `${feature.propertyName}` |")
                        if (feature.since.isNotEmpty()) {
                            appendLine("| Since | ${feature.since} |")
                        }

                        // YouTrack issues
                        if (feature.youtrackIssues.isNotEmpty()) {
                            appendLine()
                            appendLine("**Related Issues:**")
                            feature.youtrackIssues.forEach { issue ->
                                appendLine("- [$issue](${FeatureRegistry.FeatureInfo.getYouTrackUrl(issue)})")
                            }
                        }

                        appendLine()
                    }
                }
            }

            // Index of all YouTrack issues
            val allIssues = registry.getAllYouTrackIssues()
            if (allIssues.isNotEmpty()) {
                appendLine("## YouTrack Issue Index")
                appendLine()
                appendLine("All PyCharm/YouTrack issues referenced by features in this plugin:")
                appendLine()

                allIssues.sorted().forEach { issue ->
                    val relatedFeatures = registry.getFeaturesByYouTrackIssue(issue)
                    val featureNames = relatedFeatures.joinToString(", ") { it.displayName }
                    appendLine("- [$issue](${FeatureRegistry.FeatureInfo.getYouTrackUrl(issue)}) - $featureNames")
                }
                appendLine()
            }
        }
    }

    /**
     * Generates a JSON representation of all features.
     * Useful for external tooling or API consumption.
     *
     * @param includeHidden Whether to include hidden features
     * @return JSON-formatted string
     */
    fun generateJson(includeHidden: Boolean = false): String {
        val registry = FeatureRegistry.instance()
        val features = if (includeHidden) {
            registry.getAllFeatures()
        } else {
            registry.getVisibleFeatures()
        }

        return buildString {
            appendLine("{")
            appendLine("  \"features\": [")

            features.forEachIndexed { index, feature ->
                appendLine("    {")
                appendLine("      \"id\": \"${feature.id}\",")
                appendLine("      \"displayName\": \"${escapeJson(feature.displayName)}\",")
                appendLine("      \"description\": \"${escapeJson(feature.description)}\",")
                appendLine("      \"maturity\": \"${feature.maturity.name}\",")
                appendLine("      \"category\": \"${feature.category.name}\",")
                appendLine("      \"propertyName\": \"${feature.propertyName}\",")
                appendLine("      \"since\": \"${feature.since}\",")
                appendLine("      \"removeIn\": \"${feature.removeIn}\",")
                appendLine("      \"youtrackIssues\": [${feature.youtrackIssues.joinToString(", ") { "\"$it\"" }}],")
                appendLine("      \"enabled\": ${feature.isEnabled()}")
                append("    }")
                if (index < features.size - 1) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }

            appendLine("  ]")
            appendLine("}")
        }
    }

    /**
     * Generates a simple feature list for quick reference.
     *
     * @param includeHidden Whether to include hidden features
     * @return Simple text list of features
     */
    fun generateSimpleList(includeHidden: Boolean = false): String {
        val registry = FeatureRegistry.instance()
        val features = if (includeHidden) {
            registry.getAllFeatures()
        } else {
            registry.getVisibleFeatures()
        }

        return buildString {
            appendLine("BetterPy Features")
            appendLine("=================")
            appendLine()

            FeatureCategory.entries.forEach { category ->
                val categoryFeatures = features
                    .filter { it.category == category }
                    .sortedBy { it.displayName }

                if (categoryFeatures.isNotEmpty()) {
                    appendLine("${category.displayName}:")
                    categoryFeatures.forEach { feature ->
                        val status = when (feature.maturity) {
                            FeatureMaturity.INCUBATING -> " [INCUBATING]"
                            FeatureMaturity.DEPRECATED -> " [DEPRECATED]"
                            FeatureMaturity.HIDDEN -> " [HIDDEN]"
                            FeatureMaturity.STABLE -> ""
                        }
                        val enabled = if (feature.isEnabled()) "✓" else "✗"
                        appendLine("  $enabled ${feature.displayName}$status")
                    }
                    appendLine()
                }
            }
        }
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
