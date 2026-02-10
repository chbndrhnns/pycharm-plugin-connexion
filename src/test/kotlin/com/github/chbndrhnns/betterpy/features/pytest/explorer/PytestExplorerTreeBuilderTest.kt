package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.github.chbndrhnns.betterpy.features.pytest.explorer.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.swing.tree.DefaultMutableTreeNode

class PytestExplorerTreeBuilderTest {

    @Test
    fun `empty snapshot produces empty root`() {
        val snapshot = CollectionSnapshot(0, emptyList(), emptyList(), emptyList())
        val root = PytestExplorerTreeBuilder.buildTestTree(snapshot)
        assertEquals(0, root.childCount)
        assertEquals("Tests", root.userObject)
    }

    @Test
    fun `tests grouped by module`() {
        val snapshot = CollectionSnapshot(
            timestamp = 0,
            tests = listOf(
                CollectedTest("a.py::test_one", "a.py", null, "test_one", emptyList()),
                CollectedTest("b.py::test_two", "b.py", null, "test_two", emptyList()),
            ),
            fixtures = emptyList(),
            errors = emptyList(),
        )
        val root = PytestExplorerTreeBuilder.buildTestTree(snapshot)
        assertEquals(2, root.childCount)
        val first = root.getChildAt(0) as DefaultMutableTreeNode
        assertEquals("a.py", (first.userObject as ModuleTreeNode).path)
        val second = root.getChildAt(1) as DefaultMutableTreeNode
        assertEquals("b.py", (second.userObject as ModuleTreeNode).path)
    }

    @Test
    fun `tests grouped by class within module`() {
        val snapshot = CollectionSnapshot(
            timestamp = 0,
            tests = listOf(
                CollectedTest("t.py::MyClass::test_a", "t.py", "MyClass", "test_a", emptyList()),
                CollectedTest("t.py::MyClass::test_b", "t.py", "MyClass", "test_b", emptyList()),
                CollectedTest("t.py::test_standalone", "t.py", null, "test_standalone", emptyList()),
            ),
            fixtures = emptyList(),
            errors = emptyList(),
        )
        val root = PytestExplorerTreeBuilder.buildTestTree(snapshot)
        assertEquals(1, root.childCount)
        val moduleNode = root.getChildAt(0) as DefaultMutableTreeNode
        // Module should have: ClassNode + standalone test
        assertEquals(2, moduleNode.childCount)

        val classNode = moduleNode.getChildAt(0) as DefaultMutableTreeNode
        assertTrue(classNode.userObject is ClassTreeNode)
        assertEquals("MyClass", (classNode.userObject as ClassTreeNode).name)
        assertEquals(2, classNode.childCount)

        val standaloneNode = moduleNode.getChildAt(1) as DefaultMutableTreeNode
        assertTrue(standaloneNode.userObject is TestTreeNode)
        assertEquals("test_standalone", (standaloneNode.userObject as TestTreeNode).test.functionName)
    }

    @Test
    fun `modules sorted alphabetically`() {
        val snapshot = CollectionSnapshot(
            timestamp = 0,
            tests = listOf(
                CollectedTest("z.py::test_z", "z.py", null, "test_z", emptyList()),
                CollectedTest("a.py::test_a", "a.py", null, "test_a", emptyList()),
                CollectedTest("m.py::test_m", "m.py", null, "test_m", emptyList()),
            ),
            fixtures = emptyList(),
            errors = emptyList(),
        )
        val root = PytestExplorerTreeBuilder.buildTestTree(snapshot)
        val paths = (0 until root.childCount).map {
            ((root.getChildAt(it) as DefaultMutableTreeNode).userObject as ModuleTreeNode).path
        }
        assertEquals(listOf("a.py", "m.py", "z.py"), paths)
    }

    @Test
    fun `fixture tree with no dependencies`() {
        val fixtureMap = mapOf(
            "db" to CollectedFixture("db", "function", "conftest.py", "db", emptyList()),
        )
        val root = PytestExplorerTreeBuilder.buildFixtureTree(listOf("db"), fixtureMap)
        assertEquals(1, root.childCount)
        val node = root.getChildAt(0) as DefaultMutableTreeNode
        val display = node.userObject as FixtureDisplayNode
        assertEquals("db", display.name)
        assertEquals("function", display.scope)
        assertEquals(0, node.childCount)
    }

    @Test
    fun `fixture tree with transitive dependencies`() {
        val fixtureMap = mapOf(
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
        val root = PytestExplorerTreeBuilder.buildFixtureTree(listOf("db_session"), fixtureMap)
        assertEquals(1, root.childCount)
        val sessionNode = root.getChildAt(0) as DefaultMutableTreeNode
        assertEquals(1, sessionNode.childCount) // db_engine
        val engineNode = sessionNode.getChildAt(0) as DefaultMutableTreeNode
        assertEquals("db_engine", (engineNode.userObject as FixtureDisplayNode).name)
        assertEquals(1, engineNode.childCount) // settings
        val settingsNode = engineNode.getChildAt(0) as DefaultMutableTreeNode
        assertEquals("settings", (settingsNode.userObject as FixtureDisplayNode).name)
    }

    @Test
    fun `fixture tree handles circular dependency`() {
        val fixtureMap = mapOf(
            "a" to CollectedFixture("a", "function", "conftest.py", "a", listOf("b")),
            "b" to CollectedFixture("b", "function", "conftest.py", "b", listOf("a")),
        )
        val root = PytestExplorerTreeBuilder.buildFixtureTree(listOf("a"), fixtureMap)
        val aNode = root.getChildAt(0) as DefaultMutableTreeNode
        val bNode = aNode.getChildAt(0) as DefaultMutableTreeNode
        assertEquals("b", (bNode.userObject as FixtureDisplayNode).name)
        // b's dependency on a should show circular marker
        val circularNode = bNode.getChildAt(0) as DefaultMutableTreeNode
        val circularDisplay = circularNode.userObject as FixtureDisplayNode
        assertEquals("a", circularDisplay.name)
        assertEquals("⟳ circular", circularDisplay.definedIn)
    }

    @Test
    fun `fixture tree with unknown fixture shows question marks`() {
        val root = PytestExplorerTreeBuilder.buildFixtureTree(listOf("unknown"), emptyMap())
        val node = root.getChildAt(0) as DefaultMutableTreeNode
        val display = node.userObject as FixtureDisplayNode
        assertEquals("unknown", display.name)
        assertEquals("?", display.scope)
        assertEquals("?", display.definedIn)
    }
}
