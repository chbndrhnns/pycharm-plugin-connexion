package com.github.chbndrhnns.intellijplatformplugincopy.pytest

/**
 * Test failure diffs are stored in [com.github.chbndrhnns.intellijplatformplugincopy.services.TestFailureState]
 * using a key derived from [com.intellij.execution.testframework.sm.runner.SMTestProxy.locationUrl] plus
 * parametrization information from [com.intellij.execution.testframework.sm.runner.SMTestProxy.metainfo].
 */
object PytestTestKey {
    fun build(locationUrl: String, metainfo: String?): String {
        if (metainfo.isNullOrEmpty()) return locationUrl

        // For parametrized tests, metainfo typically looks like "test_name[params...]".
        // The test runner keeps locationUrl without parameters, so we append the bracket suffix.
        val bracketStart = metainfo.indexOf('[')
        if (bracketStart == -1) return locationUrl

        return locationUrl + metainfo.substring(bracketStart)
    }
}
