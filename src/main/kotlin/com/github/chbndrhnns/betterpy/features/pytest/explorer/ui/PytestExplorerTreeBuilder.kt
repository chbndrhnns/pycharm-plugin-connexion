package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import javax.swing.tree.DefaultMutableTreeNode

object PytestExplorerTreeBuilder {

    fun buildTestTree(snapshot: CollectionSnapshot, collapseModuleNode: Boolean = false): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Tests")

        val byModule = snapshot.tests.groupBy { it.modulePath }
        val skipModuleLevel = collapseModuleNode && byModule.size == 1
        for ((modulePath, tests) in byModule.toSortedMap()) {
            val moduleNode = if (skipModuleLevel) root else DefaultMutableTreeNode(ModuleTreeNode(modulePath))

            val byClass = tests.groupBy { it.className }
            for ((className, classTests) in byClass) {
                val parent = if (className != null) {
                    val classNode = DefaultMutableTreeNode(ClassTreeNode(className))
                    moduleNode.add(classNode)
                    classNode
                } else {
                    moduleNode
                }

                for (test in classTests) {
                    parent.add(DefaultMutableTreeNode(TestTreeNode(test)))
                }
            }

            if (!skipModuleLevel) {
                root.add(moduleNode)
            }
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
