package com.github.chbndrhnns.intellijplatformplugincopy.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

data class DiffData(val expected: String, val actual: String)

@Service(Service.Level.PROJECT)
class TestFailureState(private val project: Project) {

    private val failures = ConcurrentHashMap<String, DiffData>()

    fun getDiffData(locationUrl: String): DiffData? {
        return failures[locationUrl]
    }

    fun setDiffData(locationUrl: String, diffData: DiffData) {
        failures[locationUrl] = diffData
    }

    fun clearDiffData(locationUrl: String) {
        failures.remove(locationUrl)
    }

    fun clearAll() {
        failures.clear()
    }

    fun getAllKeys(): Set<String> {
        return failures.keys.toSet()
    }

    companion object {
        fun getInstance(project: Project): TestFailureState {
            return project.getService(TestFailureState::class.java)
        }
    }
}
