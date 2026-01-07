package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

data class OutcomeDiff(
    val expected: String,
    val actual: String,
    val failedLine: Int = -1
)
