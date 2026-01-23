package com.github.chbndrhnns.betterpy.features.pytest.outcome

import com.github.chbndrhnns.betterpy.core.pytest.PytestNodeIdUtil

/**
 * Builds keys used by the "use actual outcome" flow.
 *
 * The key is based on `SMTestProxy.locationUrl` plus a parametrization suffix derived from `SMTestProxy.metainfo`.
 */
object PytestTestKeyFactory {
    fun fromTestProxy(locationUrl: String, metainfo: String?): String {
        return PytestNodeIdUtil.appendMetainfoSuffix(locationUrl, metainfo)
    }
}
