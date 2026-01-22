package com.github.chbndrhnns.intellijplatformplugincopy.features.exports

import fixtures.TestBase

class PyMissingInDunderAllInspectionPrivatePackageTest : TestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
    }

    fun testWarningSuppressedInPrivatePackage() {
        // src/pub/__init__.py
        myFixture.addFileToProject(
            "pub/__init__.py",
            """
            __all__ = ["MyClass"]
            from ._privatep._private_m import MyClass
            """.trimIndent()
        )

        // src/pub/_privatep/__init__.py
        myFixture.addFileToProject(
            "pub/_privatep/__init__.py",
            ""
        )

        // src/pub/_privatep/_private_m.py
        val privateModule = myFixture.addFileToProject(
            "pub/_privatep/_private_m.py",
            """
            class MyClass:
                pass
            """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(privateModule.virtualFile)

        // We expect NO warnings because the symbol is inside a private package (_privatep)
        // AND it is actually exported by the parent public package (pub).
        // But the current implementation likely flags it because it's not in _privatep/__init__.py.

        // Run highlighting and assert we see the error (confirming the bug)
        val infos = myFixture.doHighlighting()
        val errorPresent =
            infos.any { it.description?.contains("Symbol 'MyClass' is not exported in package __all__") == true }

        // In the reproduction phase, we expect this to be TRUE (bug exists).
        // Once fixed, we will change this assertion to FALSE.
        assertFalse("Bug reproduction: warning should NOT be present after fix", errorPresent)
    }

    fun testWarningPresentIfParentPublicPackageDoesNotExport() {
        // src/pub_missing/__init__.py - DOES NOT EXPORT MyClass
        myFixture.addFileToProject(
            "pub_missing/__init__.py",
            """
            __all__ = []
            """.trimIndent()
        )

        // src/pub_missing/_privatep/__init__.py
        myFixture.addFileToProject(
            "pub_missing/_privatep/__init__.py",
            ""
        )

        // src/pub_missing/_privatep/_private_m.py
        val privateModule = myFixture.addFileToProject(
            "pub_missing/_privatep/_private_m.py",
            """
            class MyClass:
                pass
            """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(privateModule.virtualFile)

        // We expect A WARNING because the symbol is not exported by the public package (pub_missing).
        // Even though it's in a private sub-package, the public entry point fails to expose it.

        val infos = myFixture.doHighlighting()
        val errorPresent =
            infos.any { it.description?.contains("Symbol 'MyClass' is not exported in package __all__") == true }

        assertTrue("Warning SHOULD be present when parent public package does not export the symbol", errorPresent)
    }
}
