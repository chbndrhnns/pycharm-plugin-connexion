package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.github.chbndrhnns.betterpy.features.pytest.explorer.ui.PytestExplorerTreeBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class PytestMarkerTreeBuilderTest {

    @Test
    fun `empty snapshot produces empty root`() {
        val snapshot = CollectionSnapshot(0, emptyList(), emptyList(), emptyList())
        val root = PytestExplorerTreeBuilder.buildMarkerTree(snapshot)
        assertEquals(0, root.childCount)
        assertEquals("Markers", root.userObject)
    }

    @Test
    fun `tests grouped by marker`() {
        val snapshot = CollectionSnapshot(
            timestamp = 0,
            tests = listOf(
                CollectedTest("a.py::test_one", "a.py", null, "test_one", emptyList(), markers = listOf("slow")),
                CollectedTest("b.py::test_two", "b.py", null, "test_two", emptyList(), markers = listOf("slow")),
            ),
            fixtures = emptyList(),
            errors = emptyList(),
        )
        val root = PytestExplorerTreeBuilder.buildMarkerTree(snapshot)
        val expected = """
            |Markers
            |  [marker] slow (2)
            |    [test] a.py::test_one
            |    [test] b.py::test_two
            |""".trimMargin()
        assertEquals(expected, PytestExplorerTreeBuilder.toTextualTree(root))
    }

    @Test
    fun `test with multiple markers appears under each marker`() {
        val snapshot = CollectionSnapshot(
            timestamp = 0,
            tests = listOf(
                CollectedTest(
                    "a.py::test_one",
                    "a.py",
                    null,
                    "test_one",
                    emptyList(),
                    markers = listOf("slow", "integration")
                ),
            ),
            fixtures = emptyList(),
            errors = emptyList(),
        )
        val root = PytestExplorerTreeBuilder.buildMarkerTree(snapshot)
        val expected = """
            |Markers
            |  [marker] integration (1)
            |    [test] a.py::test_one
            |  [marker] slow (1)
            |    [test] a.py::test_one
            |""".trimMargin()
        assertEquals(expected, PytestExplorerTreeBuilder.toTextualTree(root))
    }

    @Test
    fun `markers sorted alphabetically`() {
        val snapshot = CollectionSnapshot(
            timestamp = 0,
            tests = listOf(
                CollectedTest(
                    "a.py::test_one",
                    "a.py",
                    null,
                    "test_one",
                    emptyList(),
                    markers = listOf("zebra", "alpha", "middle")
                ),
            ),
            fixtures = emptyList(),
            errors = emptyList(),
        )
        val root = PytestExplorerTreeBuilder.buildMarkerTree(snapshot)
        val expected = """
            |Markers
            |  [marker] alpha (1)
            |    [test] a.py::test_one
            |  [marker] middle (1)
            |    [test] a.py::test_one
            |  [marker] zebra (1)
            |    [test] a.py::test_one
            |""".trimMargin()
        assertEquals(expected, PytestExplorerTreeBuilder.toTextualTree(root))
    }

    @Test
    fun `tests without markers are not shown`() {
        val snapshot = CollectionSnapshot(
            timestamp = 0,
            tests = listOf(
                CollectedTest("a.py::test_one", "a.py", null, "test_one", emptyList(), markers = emptyList()),
                CollectedTest("b.py::test_two", "b.py", null, "test_two", emptyList(), markers = listOf("slow")),
            ),
            fixtures = emptyList(),
            errors = emptyList(),
        )
        val root = PytestExplorerTreeBuilder.buildMarkerTree(snapshot)
        val expected = """
            |Markers
            |  [marker] slow (1)
            |    [test] b.py::test_two
            |""".trimMargin()
        assertEquals(expected, PytestExplorerTreeBuilder.toTextualTree(root))
    }

    @Test
    fun `tests within marker sorted by nodeId`() {
        val snapshot = CollectionSnapshot(
            timestamp = 0,
            tests = listOf(
                CollectedTest("z.py::test_z", "z.py", null, "test_z", emptyList(), markers = listOf("slow")),
                CollectedTest("a.py::test_a", "a.py", null, "test_a", emptyList(), markers = listOf("slow")),
                CollectedTest("m.py::test_m", "m.py", null, "test_m", emptyList(), markers = listOf("slow")),
            ),
            fixtures = emptyList(),
            errors = emptyList(),
        )
        val root = PytestExplorerTreeBuilder.buildMarkerTree(snapshot)
        val expected = """
            |Markers
            |  [marker] slow (3)
            |    [test] a.py::test_a
            |    [test] m.py::test_m
            |    [test] z.py::test_z
            |""".trimMargin()
        assertEquals(expected, PytestExplorerTreeBuilder.toTextualTree(root))
    }
}
