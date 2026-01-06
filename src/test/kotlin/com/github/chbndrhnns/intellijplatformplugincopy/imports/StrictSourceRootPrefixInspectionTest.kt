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

    fun testImportMissingSourceRootPrefixFromMainIsNotMarked() {
        withPluginSettings({
            enableRestoreSourceRootPrefix = true
        }) {
            // src is a source root
            myFixture.addFileToProject("src/mypackage/module.py", "def foo(): pass")
            val mainPsi = myFixture.addFileToProject("main.py", "from <caret>mypackage.module import foo\nfoo()")

            val srcDir = myFixture.findFileInTempDir("src")
            runWithSourceRoots(listOf(srcDir)) {
                myFixture.configureFromExistingVirtualFile(mainPsi.virtualFile)

                // Should NOT have a warning on 'mypackage.module' as main.py is not in source root
                val highlight = myFixture.doHighlighting().find { it.text == "mypackage.module" }
                assertNull("Should NOT find highlighting for mypackage.module from main.py", highlight)
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

    fun testImportStatementMissingSourceRootPrefixFromMainIsNotMarked() {
        withPluginSettings({
            enableRestoreSourceRootPrefix = true
        }) {
            myFixture.addFileToProject("src/mypackage/module.py", "def foo(): pass")
            val mainPsi =
                myFixture.addFileToProject("main.py", "import <caret>mypackage.module\nmypackage.module.foo()")

            val srcDir = myFixture.findFileInTempDir("src")
            runWithSourceRoots(listOf(srcDir)) {
                myFixture.configureFromExistingVirtualFile(mainPsi.virtualFile)

                val highlight =
                    myFixture.doHighlighting().find { it.description?.contains("missing source root prefix") == true }
                assertNull("Should NOT find highlighting for mypackage.module from main.py", highlight)
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

    fun testImportMissingTestSourceRootPrefixFromMainIsNotMarked() {
        withPluginSettings({
            enableRestoreSourceRootPrefix = true
        }) {
            // tests is a test source root
            myFixture.addFileToProject("tests/mypackage/module.py", "def foo(): pass")
            val mainPsi = myFixture.addFileToProject("main.py", "from <caret>mypackage.module import foo\nfoo()")

            val testsDir = myFixture.findFileInTempDir("tests")
            runWithTestSourceRoots(listOf(testsDir)) {
                myFixture.configureFromExistingVirtualFile(mainPsi.virtualFile)

                // Should NOT have a warning on 'mypackage.module'
                val highlight = myFixture.doHighlighting().find { it.text == "mypackage.module" }
                assertNull("Should NOT find highlighting for mypackage.module from main.py", highlight)
            }
        }
    }

    fun testImportConftestFromTestsRoot() {
        withPluginSettings({
            enableRestoreSourceRootPrefix = true
        }) {
            // tests is a test source root
            myFixture.addFileToProject("tests/conftest.py", "def helper(): pass")
            // bla/test_.py is in a subdirectory of tests
            val mainPsi = myFixture.addFileToProject("tests/bla/test_.py", "from <caret>conftest import helper")

            val testsDir = myFixture.findFileInTempDir("tests")
            runWithTestSourceRoots(listOf(testsDir)) {
                myFixture.configureFromExistingVirtualFile(mainPsi.virtualFile)

                // Should have a warning on 'conftest'
                val highlight = myFixture.doHighlighting().find { it.text == "conftest" }
                assertNotNull("Should find highlighting for conftest", highlight)

                // Check for quickfix
                val fix = myFixture.findSingleIntention("Prepend source root prefix 'tests'")
                myFixture.launchAction(fix)

                myFixture.checkResult(
                    """
                    from tests.conftest import helper
                    """.trimIndent()
                )
            }
        }
    }

    fun testRelativeImportDoesNotTriggerInspection() {
        withPluginSettings({
            enableRestoreSourceRootPrefix = true
        }) {
            myFixture.addFileToProject("src/mypackage/__init__.py", "")
            myFixture.addFileToProject("src/mypackage/module.py", "def foo(): pass")
            val subPsi = myFixture.addFileToProject("src/mypackage/sub.py", "from .module import foo")

            val srcDir = myFixture.findFileInTempDir("src")
            runWithSourceRoots(listOf(srcDir)) {
                myFixture.configureFromExistingVirtualFile(subPsi.virtualFile)

                val highlights = myFixture.doHighlighting()
                val ourHighlight = highlights.find { it.description?.contains("missing source root prefix") == true }
                assertNull("Relative import should not trigger 'missing source root prefix' highlighting", ourHighlight)
            }
        }
    }

    fun testImportFromSameSourceRootIsMarkedAsUnresolved() {
        withPluginSettings({
            enableRestoreSourceRootPrefix = true
        }) {
            myFixture.addFileToProject("src/mypackage/b.py", "def foo(): pass")
            val aPsi = myFixture.addFileToProject("src/mypackage/a.py", "from <caret>mypackage.b import foo")

            val srcDir = myFixture.findFileInTempDir("src")
            runWithSourceRoots(listOf(srcDir)) {
                myFixture.configureFromExistingVirtualFile(aPsi.virtualFile)

                val highlight = myFixture.doHighlighting().find { it.text == "mypackage.b" }
                assertNotNull("Should find highlighting for mypackage.b", highlight)

                val fix = myFixture.findSingleIntention("Prepend source root prefix 'src'")
                myFixture.launchAction(fix)
                myFixture.checkResult(
                    """
                    from src.mypackage.b import foo
                """.trimIndent()
                )
            }
        }
    }

    fun testCrossRootImportDoesNotTriggerInspection() {
        withPluginSettings({
            enableRestoreSourceRootPrefix = true
        }) {
            // src/mypackage/module.py
            myFixture.addFileToProject("src/mypackage/module.py", "def foo(): pass")

            // tests/test_foo.py importing from src without prefix
            val testPsi = myFixture.addFileToProject("tests/test_foo.py", "from mypackage.module import foo\nfoo()")

            val srcDir = myFixture.findFileInTempDir("src")
            val testsDir = myFixture.findFileInTempDir("tests")

            runWithSourceRoots(listOf(srcDir)) {
                runWithTestSourceRoots(listOf(testsDir)) {
                    myFixture.configureFromExistingVirtualFile(testPsi.virtualFile)

                    // Should NOT have a warning on 'mypackage.module' because it is a cross-root import
                    val highlights = myFixture.doHighlighting()
                    val ourHighlight =
                        highlights.find { it.description?.contains("missing source root prefix") == true }

                    assertNull(
                        "Cross-root import should not trigger 'missing source root prefix' highlighting",
                        ourHighlight
                    )
                }
            }
        }
    }
}
