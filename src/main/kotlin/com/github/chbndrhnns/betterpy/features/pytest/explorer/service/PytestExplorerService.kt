package com.github.chbndrhnns.betterpy.features.pytest.explorer.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

fun interface CollectionListener {
    fun onCollectionUpdated(snapshot: CollectionSnapshot)
}

@Service(Service.Level.PROJECT)
@com.intellij.openapi.components.State(
    name = "PytestExplorerService",
    storages = [Storage("pytestExplorer.xml")],
)
class PytestExplorerService : PersistentStateComponent<PytestExplorerService.State> {

    private val LOG = Logger.getInstance(PytestExplorerService::class.java)

    data class State(
        var lastCollectionTimestamp: Long = 0,
        var serializedTests: String = "",
        var serializedFixtures: String = "",
        var serializedErrors: String = "",
    )

    private val currentSnapshot = AtomicReference<CollectionSnapshot?>(null)
    private val listeners = CopyOnWriteArrayList<CollectionListener>()
    private var myState = State()

    private val mapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun getState(): State = myState

    override fun loadState(state: State) {
        LOG.debug("Loading persisted state (timestamp=${state.lastCollectionTimestamp})")
        myState = state
        restoreFromState()
    }

    fun getSnapshot(): CollectionSnapshot? = currentSnapshot.get()

    fun clearSnapshot() {
        currentSnapshot.set(null)
        myState = State()
    }

    fun updateSnapshot(snapshot: CollectionSnapshot) {
        LOG.info("Updating snapshot: ${snapshot.tests.size} tests, ${snapshot.fixtures.size} fixtures, ${snapshot.errors.size} errors")
        currentSnapshot.set(snapshot)
        myState.lastCollectionTimestamp = snapshot.timestamp
        myState.serializedTests = mapper.writeValueAsString(snapshot.tests)
        myState.serializedFixtures = mapper.writeValueAsString(snapshot.fixtures)
        myState.serializedErrors = mapper.writeValueAsString(snapshot.errors)
        listeners.forEach { it.onCollectionUpdated(snapshot) }
    }

    fun addListener(listener: CollectionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: CollectionListener) {
        listeners.remove(listener)
    }

    private fun restoreFromState() {
        if (myState.serializedTests.isNotEmpty()) {
            try {
                val tests = mapper.readValue(
                    myState.serializedTests,
                    mapper.typeFactory.constructCollectionType(List::class.java, CollectedTest::class.java),
                ) as List<CollectedTest>
                val fixtures = mapper.readValue(
                    myState.serializedFixtures,
                    mapper.typeFactory.constructCollectionType(List::class.java, CollectedFixture::class.java),
                ) as List<CollectedFixture>
                val errors = if (myState.serializedErrors.isNotEmpty()) {
                    mapper.readValue(
                        myState.serializedErrors,
                        mapper.typeFactory.constructCollectionType(List::class.java, String::class.java),
                    ) as List<String>
                } else {
                    emptyList()
                }
                currentSnapshot.set(
                    CollectionSnapshot(
                        timestamp = myState.lastCollectionTimestamp,
                        tests = tests,
                        fixtures = fixtures,
                        errors = errors,
                    )
                )
                LOG.info("Restored snapshot from persisted state: ${tests.size} tests, ${fixtures.size} fixtures")
            } catch (e: Exception) {
                LOG.warn("Failed to restore snapshot from persisted state", e)
                currentSnapshot.set(null)
            }
        }
    }

    companion object {
        fun getInstance(project: Project): PytestExplorerService =
            project.getService(PytestExplorerService::class.java)
    }
}
