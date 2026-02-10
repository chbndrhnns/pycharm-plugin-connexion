package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.FixtureDependencyGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureDependencyGraphTest {

    @Test
    fun testTransitiveDependencies() {
        val fixtures = mapOf(
            "db_session" to CollectedFixture(
                "db_session",
                "function",
                "conftest.py",
                "db_session",
                listOf("db_engine")
            ),
            "db_engine" to CollectedFixture("db_engine", "session", "conftest.py", "db_engine", listOf("settings")),
            "settings" to CollectedFixture("settings", "session", "conftest.py", "settings", emptyList()),
        )
        val graph = FixtureDependencyGraph(fixtures)
        val deps = graph.dependenciesOf("db_session")
        assertEquals(3, deps.size)
        assertEquals("settings", deps[0].name)
    }

    @Test
    fun testReverseDependents() {
        val fixtures = mapOf(
            "db_session" to CollectedFixture(
                "db_session",
                "function",
                "conftest.py",
                "db_session",
                listOf("db_engine")
            ),
            "db_engine" to CollectedFixture("db_engine", "session", "conftest.py", "db_engine", listOf("settings")),
            "settings" to CollectedFixture("settings", "session", "conftest.py", "settings", emptyList()),
        )
        val graph = FixtureDependencyGraph(fixtures)
        val dependents = graph.dependentsOf("settings")
        assertTrue(dependents.any { it.name == "db_engine" })
        assertTrue(dependents.any { it.name == "db_session" })
    }

    @Test
    fun testNoDependencies() {
        val fixtures = mapOf(
            "simple" to CollectedFixture("simple", "function", "conftest.py", "simple", emptyList()),
        )
        val graph = FixtureDependencyGraph(fixtures)
        val deps = graph.dependenciesOf("simple")
        assertEquals(1, deps.size)
        assertEquals("simple", deps[0].name)
    }

    @Test
    fun testUnknownFixture() {
        val graph = FixtureDependencyGraph(emptyMap())
        val deps = graph.dependenciesOf("nonexistent")
        assertEquals(0, deps.size)
    }

    @Test
    fun testCycleDetection() {
        val fixtures = mapOf(
            "a" to CollectedFixture("a", "function", "conftest.py", "a", listOf("b")),
            "b" to CollectedFixture("b", "function", "conftest.py", "b", listOf("a")),
        )
        val graph = FixtureDependencyGraph(fixtures)
        val cycles = graph.findCycles()
        assertTrue(cycles.isNotEmpty())
    }

    @Test
    fun testNoCycles() {
        val fixtures = mapOf(
            "a" to CollectedFixture("a", "function", "conftest.py", "a", listOf("b")),
            "b" to CollectedFixture("b", "function", "conftest.py", "b", emptyList()),
        )
        val graph = FixtureDependencyGraph(fixtures)
        val cycles = graph.findCycles()
        assertTrue(cycles.isEmpty())
    }

    @Test
    fun testDependentsOfLeaf() {
        val fixtures = mapOf(
            "leaf" to CollectedFixture("leaf", "session", "conftest.py", "leaf", emptyList()),
        )
        val graph = FixtureDependencyGraph(fixtures)
        val dependents = graph.dependentsOf("leaf")
        assertEquals(1, dependents.size)
        assertEquals("leaf", dependents[0].name)
    }
}
