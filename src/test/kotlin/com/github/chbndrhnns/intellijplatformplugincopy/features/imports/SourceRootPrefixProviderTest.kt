package com.github.chbndrhnns.intellijplatformplugincopy.features.imports

import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class SourceRootPrefixProviderTest : TestBase() {

    fun testRestoreSourceRootPrefix() {
        withPluginSettings({ enableRestoreSourceRootPrefix = true }) {
            // 1. Create structure using helper
            myFixture.addFileToProject("src/mypackage/module.py", "def foo(): pass")
            val mainPsi = myFixture.addFileToProject("src/main.py", "foo()")

            // Get the 'src' directory (parent of main.py)
            val srcDir = mainPsi.virtualFile.parent

            // 2. Manage source root lifecycle (adds and removes 'src' as source root)
            runWithSourceRoots(listOf(srcDir)) {

                // 3. Configure and Enable Inspection
                myFixture.configureFromExistingVirtualFile(mainPsi.virtualFile)
                myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)

                // 4. Check for Fixes
                val importFix = myFixture.availableIntentions.find {
                    it.text.contains("src.mypackage.module")
                }
                    ?: error("Could not find import fix with prefix 'src'. Available: ${myFixture.availableIntentions.map { it.text }}")

                myFixture.launchAction(importFix)
                myFixture.checkResult(
                    """
                    from src.mypackage.module import foo

                    foo()""".trimIndent()
                )
            }

        }

    }

    fun testRestoreTestSourceRootPrefix() {
        withPluginSettings({ enableRestoreSourceRootPrefix = true }) {
            // 1. Create structure using helper
            myFixture.addFileToProject("tests/mypackage/test_module.py", "def test_foo(): pass")
            val mainPsi = myFixture.addFileToProject("tests/test_main.py", "test_foo()")

            // Get the 'tests' directory (parent of test_main.py)
            val testsDir = mainPsi.virtualFile.parent

            // 2. Manage test source root lifecycle
            runWithTestSourceRoots(listOf(testsDir)) {

                // 3. Configure and Enable Inspection
                myFixture.configureFromExistingVirtualFile(mainPsi.virtualFile)
                myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)

                // 4. Check for Fixes
                val importFix = myFixture.availableIntentions.find {
                    it.text.contains("tests.mypackage.test_module")
                }
                    ?: error("Could not find import fix with prefix 'tests'. Available: ${myFixture.availableIntentions.map { it.text }}")

                myFixture.launchAction(importFix)
                myFixture.checkResult(
                    """
                    from tests.mypackage.test_module import test_foo

                    test_foo()""".trimIndent()
                )
            }
        }
    }
}