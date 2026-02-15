package com.github.chbndrhnns.betterpy.featureflags

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.BuildNumber

/**
 * Determines whether a feature should be available based on the current IDE build.
 */
object FeatureAvailability {
    data class Availability(
        val available: Boolean,
        val reason: String?
    )

    private val logger = Logger.getInstance(FeatureAvailability::class.java)
    private val currentBuild: BuildNumber by lazy { ApplicationInfo.getInstance().build }
    private val currentBuildString: String by lazy { ApplicationInfo.getInstance().build.asString() }

    fun evaluate(minBuild: String, bundledIn: String): Availability {
        val minBuildNumber = parseBuildNumber("minBuild", minBuild)
        val bundledInNumber = parseBuildNumber("bundledIn", bundledIn)

        if (minBuildNumber != null && currentBuild < minBuildNumber) {
            return Availability(
                available = false,
                reason = "Requires IDE build >= $minBuild (current: $currentBuildString)"
            )
        }

        if (bundledInNumber != null && currentBuild >= bundledInNumber) {
            return Availability(
                available = false,
                reason = "Bundled in IDE since build $bundledIn"
            )
        }

        return Availability(available = true, reason = null)
    }

    fun isAvailable(minBuild: String, bundledIn: String): Boolean =
        evaluate(minBuild, bundledIn).available

    private fun parseBuildNumber(label: String, value: String): BuildNumber? {
        if (value.isBlank()) return null
        return try {
            BuildNumber.fromString(value) ?: run {
                logger.warn("Unable to parse $label build number: $value")
                null
            }
        } catch (e: Exception) {
            logger.warn("Unable to parse $label build number: $value", e)
            null
        }
    }
}
