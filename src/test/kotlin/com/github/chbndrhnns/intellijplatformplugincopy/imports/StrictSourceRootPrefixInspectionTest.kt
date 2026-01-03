package com.github.chbndrhnns.intellijplatformplugincopy.imports

import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class StrictSourceRootPrefixInspectionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
        myFixture.enableInspections(PyStrictSourceRootImportInspection::class.java)
    }

    fun testImportMissingSourceRootPrefixIsMarkedAsUnresolved() {
        withPluginSettings({
            enableRestoreSourceRootPrefix = true
        }) {
            // src is a source root
            myFixture.addFileToProject("src/mypackage/module.py", "def foo(): pass")
            val mainPsi = myFixture.addFileToProject("main.py", "from <caret>mypackage.module import foo\nfoo()")

            val srcDir = myFixture.findFileInTempDir("src")
            runWithSourceRoots(listOf(srcDir)) {
                myFixture.configureFromExistingVirtualFile(mainPsi.virtualFile)

                // Should have a warning on 'mypackage.module'
                val highlight = myFixture.doHighlighting().find { it.text == "mypackage.module" }
                assertNotNull("Should find highlighting for mypackage.module", highlight)

                // Check for quickfix
                val fix = myFixture.findSingleIntention("Prepend source root prefix 'src'")
                myFixture.launchAction(fix)

                myFixture.checkResult(
                    """
                    from src.mypackage.module import foo
                    foo()
                    """.trimIndent()
                )
            }
        }
    }

    fun testImportWithSourceRootPrefixIsResolved() {
        withPluginSettings({
            enableRestoreSourceRootPrefix = true
        }) {
            myFixture.addFileToProject("src/mypackage/module.py", "def foo(): pass")
            val mainPsi = myFixture.addFileToProject("main.py", "from src.mypackage.module import foo\nfoo()")

            val srcDir = myFixture.findFileInTempDir("src")
            runWithSourceRoots(listOf(srcDir)) {
                myFixture.configureFromExistingVirtualFile(mainPsi.virtualFile)

                val highlights = myFixture.doHighlighting()
                val unresolved = highlights.filter { it.severity.name == "ERROR" || it.severity.name == "WARNING" }
                assertEmpty("Should have no unresolved references", unresolved)
            }
        }
    }

    fun testImportStatementMissingSourceRootPrefixIsMarkedAsUnresolved() {
        withPluginSettings({
            enableRestoreSourceRootPrefix = true
        }) {
            myFixture.addFileToProject("src/mypackage/module.py", "def foo(): pass")
            val mainPsi =
                myFixture.addFileToProject("main.py", "import <caret>mypackage.module\nmypackage.module.foo()")

            val srcDir = myFixture.findFileInTempDir("src")
            runWithSourceRoots(listOf(srcDir)) {
                myFixture.configureFromExistingVirtualFile(mainPsi.virtualFile)

                val fix = myFixture.findSingleIntention("Prepend source root prefix 'src'")
                myFixture.launchAction(fix)

                myFixture.checkResult(
                    """
                    import src.mypackage.module
                    mypackage.module.foo()
                    """.trimIndent()
                )
            }
        }
    }

    fun testImportStatementMissingSourceRootPrefixNoHighlightIfDisabled() {
        withPluginSettings({
            enableRestoreSourceRootPrefix = false
        }) {
            myFixture.addFileToProject("src/mypackage/module.py", "def foo(): pass")
            val mainPsi = myFixture.addFileToProject("main.py", "import mypackage.module\nmypackage.module.foo()")

            val srcDir = myFixture.findFileInTempDir("src")
            runWithSourceRoots(listOf(srcDir)) {
                myFixture.configureFromExistingVirtualFile(mainPsi.virtualFile)

                val highlights = myFixture.doHighlighting()
                val ourHighlight = highlights.find { it.description?.contains("missing source root prefix") == true }
                assertNull("Should not have our strict prefix highlighting", ourHighlight)
            }
        }
    }
}
