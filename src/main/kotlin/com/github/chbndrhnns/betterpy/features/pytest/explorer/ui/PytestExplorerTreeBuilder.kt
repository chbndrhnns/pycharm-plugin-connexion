package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import javax.swing.tree.DefaultMutableTreeNode

object PytestExplorerTreeBuilder {

    fun buildTestTree(
        snapshot: CollectionSnapshot,
        collapseModuleNode: Boolean = false,
        fileOrder: Boolean = false
    ): DefaultMutableTreeNode {
        val byModule = snapshot.tests.groupBy { it.modulePath }
        val useModuleAsRoot = collapseModuleNode && byModule.size == 1
        val root = if (useModuleAsRoot) {
            DefaultMutableTreeNode(ModuleTreeNode(byModule.keys.first()))
        } else {
            DefaultMutableTreeNode("Tests")
        }

        val orderedModules = if (fileOrder) byModule.entries else byModule.toSortedMap().entries
        for ((modulePath, tests) in orderedModules) {
            val moduleNode = if (useModuleAsRoot) root else DefaultMutableTreeNode(ModuleTreeNode(modulePath))

            val orderedTests = if (fileOrder) tests else tests.sortedWith(
                compareBy<CollectedTest> { extractClassChain(it).isEmpty() }
                    .thenBy { extractClassChain(it).firstOrNull() ?: "" }
                    .thenBy { it.functionName }
            )
            for (test in orderedTests) {
                val classChain = extractClassChain(test)
                val parent = getOrCreateClassChain(moduleNode, classChain)
                addTestToParent(parent, test)
            }

            if (!useModuleAsRoot) {
                root.add(moduleNode)
            }
        }

        return root
    }

    /**
     * Extracts the class chain from a test's nodeId.
     * For "t.py::Outer::Inner::test_func" returns ["Outer", "Inner"].
     * For "t.py::test_func" returns [].
     */
    private fun extractClassChain(test: CollectedTest): List<String> {
        val parts = test.nodeId.split("::")
        // parts[0] is the module path, last is the function (possibly with params)
        // everything in between is the class chain
        return if (parts.size > 2) parts.subList(1, parts.size - 1) else emptyList()
    }

    /**
     * Navigates or creates nested ClassTreeNode children under [parent] for the given [classChain].
     * Returns the deepest class node (or [parent] if the chain is empty).
     */
    private fun getOrCreateClassChain(
        parent: DefaultMutableTreeNode,
        classChain: List<String>
    ): DefaultMutableTreeNode {
        var current = parent
        for (className in classChain) {
            val existing = (0 until current.childCount)
                .map { current.getChildAt(it) as DefaultMutableTreeNode }
                .firstOrNull { it.userObject is ClassTreeNode && (it.userObject as ClassTreeNode).name == className }
            current = if (existing != null) {
                existing
            } else {
                val classNode = DefaultMutableTreeNode(ClassTreeNode(className))
                current.add(classNode)
                classNode
            }
        }
        return current
    }

    private fun addTestToParent(parent: DefaultMutableTreeNode, test: CollectedTest) {
        if (test.parametrizeIds.isNotEmpty()) {
            // Find existing test node for this function to group parametrize variants
            val existingTestNode = (0 until parent.childCount)
                .map { parent.getChildAt(it) as DefaultMutableTreeNode }
                .firstOrNull { it.userObject is TestTreeNode && (it.userObject as TestTreeNode).test.functionName == test.functionName }
            val testNode = existingTestNode ?: DefaultMutableTreeNode(TestTreeNode(test)).also { parent.add(it) }
            for (paramId in test.parametrizeIds) {
                testNode.add(DefaultMutableTreeNode(ParametrizeTreeNode(paramId, test)))
            }
        } else {
            parent.add(DefaultMutableTreeNode(TestTreeNode(test)))
        }
    }

    fun buildFlatTestTree(snapshot: CollectionSnapshot): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Tests")
        val sorted =
            snapshot.tests.sortedWith(compareBy({ it.modulePath }, { it.className ?: "" }, { it.functionName }))
        for (test in sorted) {
            val label = buildString {
                append(test.modulePath)
                if (test.className != null) {
                    append("::")
                    append(test.className)
                }
                append("::")
                append(test.functionName)
            }
            val node = DefaultMutableTreeNode(FlatTestTreeNode(label, test))
            for (paramId in test.parametrizeIds) {
                node.add(DefaultMutableTreeNode(ParametrizeTreeNode(paramId, test)))
            }
            root.add(node)
        }
        return root
    }

    fun buildFixtureTree(
        fixtureNames: List<String>,
        fixtureMap: Map<String, CollectedFixture>,
    ): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Fixtures")
        for (fixtureName in fixtureNames) {
            val fixture = fixtureMap[fixtureName]
            val node = DefaultMutableTreeNode(
                FixtureDisplayNode(fixtureName, fixture?.scope ?: "?", fixture?.definedIn ?: "?")
            )
            if (fixture != null) {
                addDependencies(node, fixture, fixtureMap, mutableSetOf(fixtureName))
            }
            root.add(node)
        }
        return root
    }

    fun findTestNode(node: DefaultMutableTreeNode, functionName: String, className: String?): DefaultMutableTreeNode? {
        val userObj = node.userObject
        if (userObj is TestTreeNode && userObj.test.functionName == functionName && userObj.test.className == className) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val found = findTestNode(child, functionName, className)
            if (found != null) return found
        }
        return null
    }

    fun findTestNodeByClassChain(
        root: DefaultMutableTreeNode,
        functionName: String,
        classChain: List<String>
    ): DefaultMutableTreeNode? {
        if (classChain.isEmpty()) {
            return findTestNode(root, functionName, null)
        }
        // Search each subtree (handles module nodes transparently)
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val userObj = child.userObject
            if (userObj is ClassTreeNode && userObj.name == classChain.first()) {
                val result = findInClassChain(child, functionName, classChain, 1)
                if (result != null) return result
            } else if (userObj is ModuleTreeNode || userObj is String) {
                // Only recurse into structural containers (module nodes, root), not into other classes
                val result = findTestNodeByClassChain(child, functionName, classChain)
                if (result != null) return result
            }
        }
        return null
    }

    private fun findInClassChain(
        node: DefaultMutableTreeNode,
        functionName: String,
        classChain: List<String>,
        depth: Int
    ): DefaultMutableTreeNode? {
        if (depth == classChain.size) {
            // We've matched all classes, now find the test function
            return (0 until node.childCount)
                .map { node.getChildAt(it) as DefaultMutableTreeNode }
                .firstOrNull { it.userObject is TestTreeNode && (it.userObject as TestTreeNode).test.functionName == functionName }
        }
        // Find next class in chain
        return (0 until node.childCount)
            .map { node.getChildAt(it) as DefaultMutableTreeNode }
            .firstOrNull { it.userObject is ClassTreeNode && (it.userObject as ClassTreeNode).name == classChain[depth] }
            ?.let { findInClassChain(it, functionName, classChain, depth + 1) }
    }

    fun findParametrizeNode(
        node: DefaultMutableTreeNode,
        functionName: String,
        className: String?,
        parametrizeId: String,
    ): DefaultMutableTreeNode? {
        val testNode = findTestNode(node, functionName, className) ?: return null
        for (i in 0 until testNode.childCount) {
            val child = testNode.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val userObj = child.userObject
            if (userObj is ParametrizeTreeNode && userObj.parametrizeId == parametrizeId) {
                return child
            }
        }
        return null
    }

    private val SCOPE_ORDER = listOf("session", "package", "module", "class", "function")

    /**
     * Builds a fixture explorer tree grouped by scope, with override detection and test consumers.
     *
     * Structure:
     *   Fixtures
     *     ├── session
     *     │   ├── db_engine [session] — conftest.py
     *     │   │   └── tests/test_db.py::test_query  (consumer)
     *     │   ...
     *     ├── function
     *     │   ├── my_fixture (2 definitions)   ← override group
     *     │   │   ├── my_fixture [function] — conftest.py
     *     │   │   └── my_fixture [function] — tests/conftest.py
     *     │   ...
     */
    fun buildFixtureExplorerTree(
        snapshot: CollectionSnapshot,
        grouping: FixtureGrouping = FixtureGrouping.BY_SCOPE,
        scopeToModule: String? = null,
    ): DefaultMutableTreeNode {
        val filtered = if (scopeToModule != null) {
            val fixtureNamesUsedByModule = snapshot.tests
                .filter { it.modulePath == scopeToModule }
                .flatMap { it.fixtures }
                .toSet()
            snapshot.copy(
                fixtures = snapshot.fixtures.filter { it.name in fixtureNamesUsedByModule },
                tests = snapshot.tests,
            )
        } else {
            snapshot
        }
        return when (grouping) {
            FixtureGrouping.BY_SCOPE -> buildFixtureExplorerByScope(filtered)
            FixtureGrouping.BY_TEST_MODULE -> buildFixtureExplorerByTestModule(filtered)
            FixtureGrouping.FLAT -> buildFixtureExplorerFlat(filtered)
        }
    }

    private fun buildFixtureExplorerByScope(
        snapshot: CollectionSnapshot,
    ): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Fixtures")
        val fixtureMap = snapshot.fixtures.associateBy { it.name }

        // Build reverse map: fixture name -> tests that use it
        val fixtureConsumers = mutableMapOf<String, MutableList<CollectedTest>>()
        for (test in snapshot.tests) {
            for (fixtureName in test.fixtures) {
                fixtureConsumers.getOrPut(fixtureName) { mutableListOf() }.add(test)
            }
        }

        // Group fixtures by scope
        val byScope = snapshot.fixtures.groupBy { it.scope }

        // Detect overrides: same name, different definedIn
        val byName = snapshot.fixtures.groupBy { it.name }

        for (scope in SCOPE_ORDER) {
            val fixturesInScope = byScope[scope] ?: continue
            val scopeNode = DefaultMutableTreeNode(ScopeGroupNode(scope))

            val sorted = fixturesInScope.sortedBy { it.name }
            for (fixture in sorted) {
                val overrides = byName[fixture.name] ?: listOf(fixture)
                // Only show override group if there are multiple definitions
                if (overrides.size > 1) {
                    // Check if we already added an override group for this name under this scope
                    val alreadyAdded = (0 until scopeNode.childCount).any {
                        val obj = (scopeNode.getChildAt(it) as DefaultMutableTreeNode).userObject
                        obj is OverrideGroupNode && obj.fixtureName == fixture.name
                    }
                    if (alreadyAdded) continue

                    val overrideNode = DefaultMutableTreeNode(
                        OverrideGroupNode(fixture.name, overrides.size)
                    )
                    for (ov in overrides) {
                        val ovNode = DefaultMutableTreeNode(
                            FixtureDisplayNode(ov.name, ov.scope, ov.definedIn)
                        )
                        overrideNode.add(ovNode)
                    }
                    addConsumers(overrideNode, fixture.name, fixtureConsumers)
                    scopeNode.add(overrideNode)
                } else {
                    val fixtureNode = DefaultMutableTreeNode(
                        FixtureDisplayNode(fixture.name, fixture.scope, fixture.definedIn)
                    )
                    addDependencies(fixtureNode, fixture, fixtureMap, mutableSetOf(fixture.name))
                    addConsumers(fixtureNode, fixture.name, fixtureConsumers)
                    scopeNode.add(fixtureNode)
                }
            }

            root.add(scopeNode)
        }

        // Handle unknown scopes
        val knownScopes = SCOPE_ORDER.toSet()
        for ((scope, fixturesInScope) in byScope) {
            if (scope in knownScopes) continue
            val scopeNode = DefaultMutableTreeNode(ScopeGroupNode(scope))
            for (fixture in fixturesInScope.sortedBy { it.name }) {
                val fixtureNode = DefaultMutableTreeNode(
                    FixtureDisplayNode(fixture.name, fixture.scope, fixture.definedIn)
                )
                addConsumers(fixtureNode, fixture.name, fixtureConsumers)
                scopeNode.add(fixtureNode)
            }
            root.add(scopeNode)
        }

        return root
    }

    /**
     * Groups fixtures by the test module that uses them.
     * Structure: Fixtures > module_path > fixture [scope] — definedIn
     */
    private fun buildFixtureExplorerByTestModule(
        snapshot: CollectionSnapshot,
    ): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Fixtures")
        val fixtureMap = snapshot.fixtures.associateBy { it.name }

        // module -> set of fixture names used by tests in that module
        val moduleFixtures = mutableMapOf<String, MutableSet<String>>()
        for (test in snapshot.tests) {
            for (fixtureName in test.fixtures) {
                moduleFixtures.getOrPut(test.modulePath) { mutableSetOf() }.add(fixtureName)
            }
        }

        for (modulePath in moduleFixtures.keys.sorted()) {
            val moduleNode = DefaultMutableTreeNode(FixtureModuleGroupNode(modulePath))
            val fixtureNames = moduleFixtures[modulePath]!!.sorted()
            for (name in fixtureNames) {
                val fixture = fixtureMap[name]
                val fixtureNode = DefaultMutableTreeNode(
                    FixtureDisplayNode(name, fixture?.scope ?: "?", fixture?.definedIn ?: "?")
                )
                if (fixture != null) {
                    addDependencies(fixtureNode, fixture, fixtureMap, mutableSetOf(name))
                }
                moduleNode.add(fixtureNode)
            }
            root.add(moduleNode)
        }

        // Add fixtures not used by any test under an "(unused)" group
        val usedNames = moduleFixtures.values.flatten().toSet()
        val unused = snapshot.fixtures.filter { it.name !in usedNames }.sortedBy { it.name }
        if (unused.isNotEmpty()) {
            val unusedNode = DefaultMutableTreeNode(FixtureModuleGroupNode("(unused)"))
            for (fixture in unused) {
                unusedNode.add(
                    DefaultMutableTreeNode(
                        FixtureDisplayNode(fixture.name, fixture.scope, fixture.definedIn)
                    )
                )
            }
            root.add(unusedNode)
        }

        return root
    }

    /**
     * Flat alphabetical list of all fixtures.
     */
    private fun buildFixtureExplorerFlat(
        snapshot: CollectionSnapshot,
    ): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Fixtures")
        val fixtureMap = snapshot.fixtures.associateBy { it.name }
        val fixtureConsumers = mutableMapOf<String, MutableList<CollectedTest>>()
        for (test in snapshot.tests) {
            for (fixtureName in test.fixtures) {
                fixtureConsumers.getOrPut(fixtureName) { mutableListOf() }.add(test)
            }
        }

        for (fixture in snapshot.fixtures.sortedBy { it.name }) {
            val fixtureNode = DefaultMutableTreeNode(
                FixtureDisplayNode(fixture.name, fixture.scope, fixture.definedIn)
            )
            addDependencies(fixtureNode, fixture, fixtureMap, mutableSetOf(fixture.name))
            addConsumers(fixtureNode, fixture.name, fixtureConsumers)
            root.add(fixtureNode)
        }

        return root
    }

    private fun addConsumers(
        parentNode: DefaultMutableTreeNode,
        fixtureName: String,
        fixtureConsumers: Map<String, List<CollectedTest>>,
    ) {
        val consumers = fixtureConsumers[fixtureName] ?: return
        for (test in consumers.sortedBy { it.nodeId }) {
            parentNode.add(DefaultMutableTreeNode(TestConsumerNode(test)))
        }
    }

    private fun addDependencies(
        parentNode: DefaultMutableTreeNode,
        fixture: CollectedFixture,
        fixtureMap: Map<String, CollectedFixture>,
        visited: MutableSet<String>,
    ) {
        for (depName in fixture.dependencies) {
            if (!visited.add(depName)) {
                parentNode.add(
                    DefaultMutableTreeNode(
                        FixtureDisplayNode(depName, "?", "⟳ circular")
                    )
                )
                continue
            }
            val dep = fixtureMap[depName]
            val depNode = DefaultMutableTreeNode(
                FixtureDisplayNode(depName, dep?.scope ?: "?", dep?.definedIn ?: "?")
            )
            if (dep != null) {
                addDependencies(depNode, dep, fixtureMap, visited)
            }
            parentNode.add(depNode)
        }
    }
}
