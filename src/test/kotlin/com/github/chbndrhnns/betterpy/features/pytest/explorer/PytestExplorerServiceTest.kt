package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.github.chbndrhnns.betterpy.features.pytest.explorer.service.CollectionListener
import com.github.chbndrhnns.betterpy.features.pytest.explorer.service.PytestExplorerService
import fixtures.TestBase

class PytestExplorerServiceTest : TestBase() {

    private fun getService(): PytestExplorerService =
        PytestExplorerService.getInstance(project)

    override fun setUp() {
        super.setUp()
        getService().clearSnapshot()
    }

    private fun createSnapshot(
        timestamp: Long = System.currentTimeMillis(),
        tests: List<CollectedTest> = emptyList(),
        fixtures: List<CollectedFixture> = emptyList(),
        errors: List<String> = emptyList(),
    ) = CollectionSnapshot(timestamp, tests, fixtures, errors)

    fun testInitialSnapshotIsNull() {
        val service = getService()
        assertNull(service.getSnapshot())
    }

    fun testUpdateSnapshotStoresSnapshot() {
        val service = getService()
        val snapshot = createSnapshot(
            tests = listOf(
                CollectedTest("test.py::test_a", "test.py", null, "test_a", listOf("db"), emptyList())
            ),
            fixtures = listOf(
                CollectedFixture("db", "function", "conftest.py", "db", emptyList(), false)
            ),
        )
        service.updateSnapshot(snapshot)
        val stored = service.getSnapshot()
        assertNotNull(stored)
        assertEquals(snapshot.timestamp, stored!!.timestamp)
        assertEquals(1, stored.tests.size)
        assertEquals("test_a", stored.tests[0].functionName)
        assertEquals(1, stored.fixtures.size)
        assertEquals("db", stored.fixtures[0].name)
    }

    fun testUpdateSnapshotReplacesOldSnapshot() {
        val service = getService()
        service.updateSnapshot(createSnapshot(timestamp = 100))
        service.updateSnapshot(createSnapshot(timestamp = 200))
        assertEquals(200, service.getSnapshot()!!.timestamp)
    }

    fun testListenerNotifiedOnUpdate() {
        val service = getService()
        var notified: CollectionSnapshot? = null
        val listener = CollectionListener { snapshot -> notified = snapshot }
        service.addListener(listener)

        val snapshot = createSnapshot()
        service.updateSnapshot(snapshot)

        assertNotNull("Listener should have been notified", notified)
        assertEquals(snapshot.timestamp, notified!!.timestamp)
    }

    fun testRemovedListenerNotNotified() {
        val service = getService()
        var callCount = 0
        val listener = CollectionListener { callCount++ }
        service.addListener(listener)
        service.removeListener(listener)

        service.updateSnapshot(createSnapshot())
        assertEquals("Removed listener should not be called", 0, callCount)
    }

    fun testSerializationRoundTrip() {
        val service = getService()
        val tests = listOf(
            CollectedTest(
                "test.py::TestAuth::test_login",
                "test.py",
                "TestAuth",
                "test_login",
                listOf("db", "settings"),
                listOf("param1")
            ),
            CollectedTest("test.py::test_standalone", "test.py", null, "test_standalone", emptyList(), emptyList()),
        )
        val fixtures = listOf(
            CollectedFixture("db", "function", "conftest.py", "db", listOf("engine"), false),
            CollectedFixture("engine", "session", "conftest.py", "engine", emptyList(), false),
            CollectedFixture("cleanup", "function", "conftest.py", "cleanup", emptyList(), true),
        )
        val snapshot =
            createSnapshot(timestamp = 12345, tests = tests, fixtures = fixtures, errors = listOf("some warning"))

        service.updateSnapshot(snapshot)

        // Simulate persistence round-trip: get state, create new service, load state
        val state = service.state

        val service2 = PytestExplorerService()
        service2.loadState(state)

        val restored = service2.getSnapshot()
        assertNotNull("Snapshot should be restored from state", restored)
        assertEquals(12345, restored!!.timestamp)
        assertEquals(2, restored.tests.size)
        assertEquals("TestAuth", restored.tests[0].className)
        assertNull(restored.tests[1].className)
        assertEquals(listOf("db", "settings"), restored.tests[0].fixtures)
        assertEquals(listOf("param1"), restored.tests[0].parametrizeIds)
        assertEquals(3, restored.fixtures.size)
        assertEquals("session", restored.fixtures[1].scope)
        assertEquals(listOf("engine"), restored.fixtures[0].dependencies)
        assertTrue(restored.fixtures[2].isAutouse)
        assertEquals(listOf("some warning"), restored.errors)
    }

    fun testEmptyStateProducesNullSnapshot() {
        val service = PytestExplorerService()
        service.loadState(PytestExplorerService.State())
        assertNull(service.getSnapshot())
    }
}
