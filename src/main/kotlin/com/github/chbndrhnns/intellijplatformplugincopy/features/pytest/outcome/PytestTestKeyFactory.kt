package com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.outcome

import com.github.chbndrhnns.intellijplatformplugincopy.core.pytest.PytestNodeIdUtil

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
