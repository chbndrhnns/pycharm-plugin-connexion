package com.github.chbndrhnns.betterpy.features.imports

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import fixtures.TestBase

class HideTransientImportReproductionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
        PluginSettingsState.instance().state.enableHideTransientImports = true
    }

    fun testLocalSymbolIsNotFilteredOut() {
        // 1. Create a pyproject.toml with some dependencies (but NOT the current package)
        myFixture.addFileToProject(
            "pyproject.toml", """
            [project]
            name = "myproject"
            dependencies = [
                "requests"
            ]
        """.trimIndent()
        )

        // 2. Create a local package and a module with a symbol
        myFixture.addFileToProject("myproject/__init__.py", "")
        myFixture.addFileToProject("myproject/local_mod.py", "def local_func(): pass")

        // 3. Create a file that tries to use the local symbol
        val content = "local_func<caret>()"
        val file = myFixture.addFileToProject("main.py", content)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        // 4. Check if the import suggestion is available
        val intentions = myFixture.availableIntentions
        val importOption = intentions.find { it.text.contains("Import") && it.text.contains("local_func") }

        assertNotNull(
            "Import suggestion for 'local_func' should be available even if 'myproject' is not in dependencies",
            importOption
        )
    }

    fun testDependencySymbolIsNotFilteredOut() {
        // 1. Create a pyproject.toml with 'requests'
        myFixture.addFileToProject(
            "pyproject.toml", """
            [project]
            name = "myproject"
            dependencies = [
                "requests"
            ]
        """.trimIndent()
        )

        // 2. Create a 'requests' package (simulated)
        myFixture.addFileToProject("requests/__init__.py", "def get(): pass")

        // 3. Use it
        val content = "get<caret>()"
        val file = myFixture.addFileToProject("main.py", content)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        // 4. Check if available
        val intentions = myFixture.availableIntentions
        val importOption = intentions.find { it.text.contains("Import") && it.text.contains("requests") }

        assertNotNull(
            "Import suggestion for 'requests.get' should be available because 'requests' is in dependencies",
            importOption
        )
    }
}
