package com.github.chbndrhnns.betterpy.features.pytest.explorer.model

class FixtureDependencyGraph(private val fixtures: Map<String, CollectedFixture>) {

    fun dependenciesOf(fixtureName: String): List<CollectedFixture> {
        val visited = mutableSetOf<String>()
        val result = mutableListOf<CollectedFixture>()
        fun dfs(name: String) {
            if (!visited.add(name)) return
            val fixture = fixtures[name] ?: return
            for (dep in fixture.dependencies) {
                dfs(dep)
            }
            result.add(fixture)
        }
        dfs(fixtureName)
        return result
    }

    fun dependentsOf(fixtureName: String): List<CollectedFixture> {
        val reverseMap = mutableMapOf<String, MutableList<String>>()
        for ((name, fixture) in fixtures) {
            for (dep in fixture.dependencies) {
                reverseMap.getOrPut(dep) { mutableListOf() }.add(name)
            }
        }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<CollectedFixture>()
        fun dfs(name: String) {
            if (!visited.add(name)) return
            fixtures[name]?.let { result.add(it) }
            reverseMap[name]?.forEach { dfs(it) }
        }
        dfs(fixtureName)
        return result
    }

    fun findCycles(): List<List<String>> {
        val visited = mutableSetOf<String>()
        val onStack = mutableSetOf<String>()
        val cycles = mutableListOf<List<String>>()

        fun dfs(name: String, path: MutableList<String>) {
            if (name in onStack) {
                val cycleStart = path.indexOf(name)
                if (cycleStart >= 0) {
                    cycles.add(path.subList(cycleStart, path.size).toList() + name)
                }
                return
            }
            if (name in visited) return
            visited.add(name)
            onStack.add(name)
            path.add(name)
            val fixture = fixtures[name]
            if (fixture != null) {
                for (dep in fixture.dependencies) {
                    dfs(dep, path)
                }
            }
            path.removeAt(path.lastIndex)
            onStack.remove(name)
        }

        for (name in fixtures.keys) {
            dfs(name, mutableListOf())
        }
        return cycles
    }
}
