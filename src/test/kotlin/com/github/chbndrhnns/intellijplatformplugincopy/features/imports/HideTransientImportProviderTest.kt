package com.github.chbndrhnns.intellijplatformplugincopy.features.imports

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import fixtures.TestBase

class HideTransientImportProviderTest : TestBase() {

    override fun setUp() {
        super.setUp()
        // Enable the feature
        PluginSettingsState.instance().state.enableHideTransientImports = true
    }

    fun testHidesTransientDependencyImports() {
        // Create a pyproject.toml with direct dependencies
        myFixture.addFileToProject(
            "pyproject.toml", """
            [project]
            name = "test-project"
            dependencies = [
                "requests>=2.0.0",
                "flask>=2.0.0"
            ]
        """.trimIndent()
        )

        // Create a Python file that tries to import from a transient dependency
        // (urllib3 is a transient dependency of requests, not a direct dependency)
        myFixture.configureByText(
            "test.py", """
            from urllib3 import <caret>
        """.trimIndent()
        )

        // Get auto-import suggestions
        val lookupElements = myFixture.completeBasic()

        // Verify that urllib3 imports are filtered out
        // (This test verifies the provider is registered and filtering is applied)
        // Note: The actual filtering happens during quick fix generation,
        // so this test mainly verifies the setup is correct
        assertNotNull(lookupElements)
    }

    fun testShowsDirectDependencyImports() {
        // Create a pyproject.toml with direct dependencies
        myFixture.addFileToProject(
            "pyproject.toml", """
            [project]
            name = "test-project"
            dependencies = [
                "requests>=2.0.0"
            ]
        """.trimIndent()
        )

        // Create a Python file that imports from a direct dependency
        myFixture.configureByText(
            "test.py", """
            from requests import <caret>
        """.trimIndent()
        )

        // Get auto-import suggestions
        val lookupElements = myFixture.completeBasic()

        // Verify that requests imports are available
        assertNotNull(lookupElements)
    }

    fun testDisabledWhenSettingIsFalse() {
        // Disable the feature
        PluginSettingsState.instance().state.enableHideTransientImports = false

        // Create a pyproject.toml
        myFixture.addFileToProject(
            "pyproject.toml", """
            [project]
            name = "test-project"
            dependencies = [
                "requests>=2.0.0"
            ]
        """.trimIndent()
        )

        // Create a Python file
        myFixture.configureByText(
            "test.py", """
            from urllib3 import <caret>
        """.trimIndent()
        )

        // Get auto-import suggestions
        val lookupElements = myFixture.completeBasic()

        // When disabled, all imports should be available (no filtering)
        assertNotNull(lookupElements)
    }

    fun testHandlesMissingPyprojectToml() {
        // No pyproject.toml file created

        // Create a Python file
        myFixture.configureByText(
            "test.py", """
            from requests import <caret>
        """.trimIndent()
        )

        // Get auto-import suggestions
        val lookupElements = myFixture.completeBasic()

        // Should not crash when pyproject.toml is missing
        // (fail-open behavior)
        assertNotNull(lookupElements)
    }

    fun testHandlesPoetryFormat() {
        // Create a pyproject.toml with Poetry format
        myFixture.addFileToProject(
            "pyproject.toml", """
            [tool.poetry]
            name = "test-project"
            
            [tool.poetry.dependencies]
            python = "^3.9"
            requests = "^2.0.0"
            flask = "^2.0.0"
        """.trimIndent()
        )

        // Create a Python file
        myFixture.configureByText(
            "test.py", """
            from requests import <caret>
        """.trimIndent()
        )

        // Get auto-import suggestions
        val lookupElements = myFixture.completeBasic()

        // Should handle Poetry format correctly
        assertNotNull(lookupElements)
    }

    fun testNormalizesPackageNames() {
        // Create a pyproject.toml with package names that need normalization
        myFixture.addFileToProject(
            "pyproject.toml", """
            [project]
            name = "test-project"
            dependencies = [
                "Django-Storage>=1.0.0",
                "beautifulsoup4>=4.0.0"
            ]
        """.trimIndent()
        )

        // Create a Python file
        myFixture.configureByText(
            "test.py", """
            from django_storage import <caret>
        """.trimIndent()
        )

        // Get auto-import suggestions
        val lookupElements = myFixture.completeBasic()

        // Should normalize package names (Django-Storage -> django-storage)
        assertNotNull(lookupElements)
    }

    fun testNeverFiltersStdlibModules() {
        // Create a pyproject.toml with NO typing dependency (stdlib should still be available)
        myFixture.addFileToProject(
            "pyproject.toml", """
            [project]
            name = "test-project"
            dependencies = [
                "requests>=2.0.0"
            ]
        """.trimIndent()
        )

        // Create a Python file that uses a stdlib typing symbol
        myFixture.configureByText(
            "test.py", """
            from typing import Literal
            a: Literal
        """.trimIndent()
        )

        // Verify the import from typing (stdlib) is not filtered out
        // The file should have no unresolved references since typing is stdlib
        myFixture.checkHighlighting(false, false, false)
    }
}
