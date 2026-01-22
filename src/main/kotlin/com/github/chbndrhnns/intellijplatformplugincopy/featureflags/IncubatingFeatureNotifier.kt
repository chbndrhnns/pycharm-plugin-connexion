package com.github.chbndrhnns.intellijplatformplugincopy.featureflags

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Notifies users about enabled incubating or deprecated features on project startup.
 * This helps users understand that some features they're using may change or be removed.
 */
class IncubatingFeatureNotifier : ProjectActivity {

    override suspend fun execute(project: Project) {
        val properties = PropertiesComponent.getInstance()
        val registry = FeatureRegistry.instance()

        val enabledIncubating = registry.getEnabledIncubatingFeatures()
        val enabledDeprecated = registry.getEnabledDeprecatedFeatures()

        if (enabledIncubating.isEmpty() && enabledDeprecated.isEmpty()) {
            return
        }

        // Create a hash of currently enabled features
        val currentFeaturesHash = calculateFeaturesHash(enabledIncubating, enabledDeprecated)
        val lastShownHash = properties.getValue(FEATURES_HASH_KEY, "")

        // Check if notification has already been shown for this set of features
        if (currentFeaturesHash == lastShownHash) {
            return
        }

        // Mark notification as shown for this set of features
        properties.setValue(FEATURES_HASH_KEY, currentFeaturesHash)

        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?: return

        // Notify about incubating features
        if (enabledIncubating.isNotEmpty()) {
            val featureNames = enabledIncubating.take(MAX_FEATURES_TO_SHOW)
                .joinToString(", ") { it.displayName }
            val moreCount = (enabledIncubating.size - MAX_FEATURES_TO_SHOW).coerceAtLeast(0)
            val moreText = if (moreCount > 0) " and $moreCount more" else ""

            val content = buildString {
                append("You have ${enabledIncubating.size} experimental feature(s) enabled: ")
                append(featureNames)
                append(moreText)
                append(". These may change or be removed in future versions.")
            }

            notificationGroup.createNotification(
                INCUBATING_TITLE,
                content,
                NotificationType.INFORMATION
            ).notify(project)
        }

        // Notify about deprecated features
        if (enabledDeprecated.isNotEmpty()) {
            val featureNames = enabledDeprecated.take(MAX_FEATURES_TO_SHOW)
                .joinToString(", ") { feature ->
                    if (feature.removeIn.isNotEmpty()) {
                        "${feature.displayName} (removing in ${feature.removeIn})"
                    } else {
                        feature.displayName
                    }
                }
            val moreCount = (enabledDeprecated.size - MAX_FEATURES_TO_SHOW).coerceAtLeast(0)
            val moreText = if (moreCount > 0) " and $moreCount more" else ""

            val content = buildString {
                append("You have ${enabledDeprecated.size} deprecated feature(s) enabled: ")
                append(featureNames)
                append(moreText)
                append(". Consider disabling them before they are removed.")
            }

            notificationGroup.createNotification(
                DEPRECATED_TITLE,
                content,
                NotificationType.WARNING
            ).notify(project)
        }
    }

    private fun calculateFeaturesHash(
        incubating: List<FeatureRegistry.FeatureInfo>,
        deprecated: List<FeatureRegistry.FeatureInfo>
    ): String {
        val allFeatures = (incubating + deprecated)
            .map { it.id }
            .sorted()
            .joinToString(",")
        return allFeatures.hashCode().toString()
    }

    companion object {
        private const val NOTIFICATION_GROUP_ID = "BetterPy Notifications"
        private const val INCUBATING_TITLE = "BetterPy: Incubating Features Enabled"
        private const val DEPRECATED_TITLE = "BetterPy: Deprecated Features Enabled"
        private const val MAX_FEATURES_TO_SHOW = 3
        private const val FEATURES_HASH_KEY = "betterpy.incubating.features.hash"
    }
}
