package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.collection.IncrementalCollectionMerger
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import org.junit.Assert.*
import org.junit.Test

class IncrementalCollectionMergerTest {

    @Test
    fun `merge replaces tests from changed modules`() {
        val existing = snapshot(
            tests = listOf(
                test("tests/test_auth.py::test_login", "tests/test_auth.py"),
                test("tests/test_api.py::test_health", "tests/test_api.py"),
            ),
        )
        val newTests = listOf(
            test("tests/test_auth.py::test_login_v2", "tests/test_auth.py"),
        )
        val changedFiles = setOf("tests/test_auth.py")

        val merged = IncrementalCollectionMerger.merge(existing, newTests, emptyList(), changedFiles)

        assertEquals(2, merged.tests.size)
        assertTrue(merged.tests.any { it.nodeId == "tests/test_auth.py::test_login_v2" })
        assertTrue(merged.tests.any { it.nodeId == "tests/test_api.py::test_health" })
        assertFalse(merged.tests.any { it.nodeId == "tests/test_auth.py::test_login" })
    }

    @Test
    fun `merge keeps unchanged modules intact`() {
        val existing = snapshot(
            tests = listOf(
                test("tests/test_auth.py::test_login", "tests/test_auth.py"),
                test("tests/test_api.py::test_health", "tests/test_api.py"),
                test("tests/test_db.py::test_query", "tests/test_db.py"),
            ),
        )
        val newTests = listOf(
            test("tests/test_auth.py::test_login_v2", "tests/test_auth.py"),
        )
        val changedFiles = setOf("tests/test_auth.py")

        val merged = IncrementalCollectionMerger.merge(existing, newTests, emptyList(), changedFiles)

        assertEquals(3, merged.tests.size)
        assertTrue(merged.tests.any { it.nodeId == "tests/test_api.py::test_health" })
        assertTrue(merged.tests.any { it.nodeId == "tests/test_db.py::test_query" })
    }

    @Test
    fun `merge with empty changed files returns new snapshot with all new tests`() {
        val existing = snapshot(
            tests = listOf(
                test("tests/test_auth.py::test_login", "tests/test_auth.py"),
            ),
        )
        val newTests = listOf(
            test("tests/test_auth.py::test_login_v2", "tests/test_auth.py"),
            test("tests/test_api.py::test_health", "tests/test_api.py"),
        )

        val merged = IncrementalCollectionMerger.merge(existing, newTests, emptyList(), emptySet())

        assertEquals(2, merged.tests.size)
    }

    @Test
    fun `merge updates fixtures from changed modules`() {
        val existing = snapshot(
            fixtures = listOf(
                fixture("db_session", "tests/conftest.py"),
                fixture("settings", "tests/conftest.py"),
            ),
        )
        val newFixtures = listOf(
            fixture("db_session_v2", "tests/conftest.py"),
        )
        val changedFiles = setOf("tests/conftest.py")

        val merged = IncrementalCollectionMerger.merge(existing, emptyList(), newFixtures, changedFiles)

        assertEquals(1, merged.fixtures.size)
        assertEquals("db_session_v2", merged.fixtures[0].name)
    }

    @Test
    fun `merge updates timestamp`() {
        val existing = snapshot(timestamp = 1000L)
        val merged = IncrementalCollectionMerger.merge(existing, emptyList(), emptyList(), emptySet())

        assertTrue(merged.timestamp > 1000L)
    }

    @Test
    fun `merge with conftest change affects directory-level fixtures`() {
        val existing = snapshot(
            tests = listOf(
                test("tests/test_auth.py::test_login", "tests/test_auth.py"),
                test("tests/sub/test_deep.py::test_deep", "tests/sub/test_deep.py"),
            ),
            fixtures = listOf(
                fixture("db", "tests/conftest.py"),
                fixture("deep_fix", "tests/sub/conftest.py"),
            ),
        )
        val newFixtures = listOf(
            fixture("db_v2", "tests/conftest.py"),
        )
        val changedFiles = setOf("tests/conftest.py")

        val merged = IncrementalCollectionMerger.merge(existing, emptyList(), newFixtures, changedFiles)

        // Tests unchanged
        assertEquals(2, merged.tests.size)
        // Only fixtures from tests/conftest.py replaced; tests/sub/conftest.py kept
        assertEquals(2, merged.fixtures.size)
        assertTrue(merged.fixtures.any { it.name == "db_v2" })
        assertTrue(merged.fixtures.any { it.name == "deep_fix" })
    }

    @Test
    fun `affectedModules extracts directory from changed files`() {
        val dirs = IncrementalCollectionMerger.affectedModules(
            setOf("tests/test_auth.py", "tests/sub/test_deep.py", "conftest.py")
        )
        assertEquals(setOf("tests", "tests/sub", ""), dirs)
    }

    // --- helpers ---

    private fun test(nodeId: String, modulePath: String) = CollectedTest(
        nodeId = nodeId,
        modulePath = modulePath,
        className = null,
        functionName = nodeId.substringAfterLast("::"),
        fixtures = emptyList(),
    )

    private fun fixture(name: String, definedIn: String) = CollectedFixture(
        name = name,
        scope = "function",
        definedIn = definedIn,
        functionName = name,
        dependencies = emptyList(),
        isAutouse = false,
    )

    private fun snapshot(
        tests: List<CollectedTest> = emptyList(),
        fixtures: List<CollectedFixture> = emptyList(),
        timestamp: Long = 1000L,
    ) = CollectionSnapshot(
        timestamp = timestamp,
        tests = tests,
        fixtures = fixtures,
        errors = emptyList(),
    )
}
