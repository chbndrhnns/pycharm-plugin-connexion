package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest

data class ModuleTreeNode(val path: String) {
    override fun toString(): String = path
}

data class ClassTreeNode(val name: String) {
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
