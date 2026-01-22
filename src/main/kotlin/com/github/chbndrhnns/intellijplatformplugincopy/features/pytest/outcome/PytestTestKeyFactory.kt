package com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.outcome

/**
 * Builds keys used by the "use actual outcome" flow.
 *
 * The key is based on `SMTestProxy.locationUrl` plus a parametrization suffix derived from `SMTestProxy.metainfo`.
 */
object PytestTestKeyFactory {
    fun fromTestProxy(locationUrl: String, metainfo: String?): String {
        if (metainfo.isNullOrEmpty()) return locationUrl

        val bracketStart = metainfo.indexOf('[')
        if (bracketStart == -1) return locationUrl

        return locationUrl + metainfo.substring(bracketStart)
    }
}
