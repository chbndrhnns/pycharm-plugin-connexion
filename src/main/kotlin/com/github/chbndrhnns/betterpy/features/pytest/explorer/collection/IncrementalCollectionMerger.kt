package com.github.chbndrhnns.betterpy.features.pytest.explorer.collection

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.intellij.openapi.diagnostic.Logger

/**
 * Merges partial collection results with an existing snapshot for incremental updates.
 *
 * When only a subset of files changed, we re-collect only the affected modules
 * and merge the results: replace entries for changed modules, keep others.
 */
object IncrementalCollectionMerger {

    private val LOG = Logger.getInstance(IncrementalCollectionMerger::class.java)

    /**
     * Merges newly collected tests/fixtures into an existing snapshot.
     *
     * @param existing The current snapshot
     * @param newTests Tests collected from changed modules
     * @param newFixtures Fixtures collected from changed modules
     * @param changedFiles Set of changed file paths (relative to project root)
     * @return A new snapshot with merged results
     */
    fun merge(
        existing: CollectionSnapshot,
        newTests: List<CollectedTest>,
        newFixtures: List<CollectedFixture>,
        changedFiles: Set<String>,
    ): CollectionSnapshot {
        LOG.debug("Merging: ${newTests.size} new tests, ${newFixtures.size} new fixtures, ${changedFiles.size} changed files")
        if (changedFiles.isEmpty()) {
            return CollectionSnapshot(
                timestamp = System.currentTimeMillis(),
                tests = newTests,
                fixtures = newFixtures,
                errors = existing.errors,
            )
        }

        val unchangedTests = existing.tests.filter { test ->
            test.modulePath !in changedFiles
        }

        val unchangedFixtures = existing.fixtures.filter { fixture ->
            fixture.definedIn !in changedFiles
        }

        val merged = CollectionSnapshot(
            timestamp = System.currentTimeMillis(),
            tests = unchangedTests + newTests,
            fixtures = unchangedFixtures + newFixtures,
            errors = existing.errors,
        )
        LOG.debug("Merge result: ${merged.tests.size} tests, ${merged.fixtures.size} fixtures (kept ${unchangedTests.size} unchanged tests)")
        return merged
    }

    /**
     * Extracts the set of affected directory paths from changed file paths.
     */
    fun affectedModules(changedFiles: Set<String>): Set<String> {
        return changedFiles.map { file ->
            val lastSlash = file.lastIndexOf('/')
            if (lastSlash >= 0) file.substring(0, lastSlash) else ""
        }.toSet()
    }
}
