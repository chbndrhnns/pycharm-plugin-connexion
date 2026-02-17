package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest

enum class FixtureGrouping {
    BY_SCOPE,
    BY_TEST_MODULE,
    FLAT,
}

data class FixtureModuleGroupNode(val modulePath: String) {
    override fun toString(): String = modulePath
}

data class ModuleTreeNode(val path: String, val isSkipped: Boolean = false) {
    override fun toString(): String = path
}

data class ClassTreeNode(val name: String, val isSkipped: Boolean = false) {
    override fun toString(): String = name
}

data class TestTreeNode(val test: CollectedTest) {
    override fun toString(): String = test.functionName
}

data class ParametrizeTreeNode(val parametrizeId: String, val test: CollectedTest) {
    override fun toString(): String = parametrizeId
}

data class FlatTestTreeNode(val label: String, val test: CollectedTest) {
    override fun toString(): String = label
}

data class FixtureDisplayNode(
    val name: String,
    val scope: String,
    val definedIn: String,
) {
    override fun toString(): String = "$name [$scope] — $definedIn"
}

data class ScopeGroupNode(val scope: String) {
    override fun toString(): String = scope
}

data class OverrideGroupNode(val fixtureName: String, val count: Int) {
    override fun toString(): String = "$fixtureName ($count definitions)"
}

data class TestConsumerNode(val test: CollectedTest) {
    override fun toString(): String = test.nodeId
}

data class MarkerGroupNode(val markerName: String, val testCount: Int) {
    override fun toString(): String = "$markerName ($testCount)"
}

data class MarkerTestNode(val test: CollectedTest) {
    override fun toString(): String = test.nodeId
}
