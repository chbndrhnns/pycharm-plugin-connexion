package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import fixtures.TestBase

class PyMissingInDunderAllInspectionTest : TestBase() {

    fun testMissingInAll() = doTest()

    fun testPresentInAll() = doTest()

    fun testNoDunderAll() = doTest()

    fun testPrivateSymbol() = doTest()

    fun testNonSequenceAllIsIgnored() = doTest()

    fun testAddToAllFix() = doTest(applyFix = true)

    fun testImportInInitAddToAllFix() = doTest(applyFix = true)

    fun testModuleMissingFromPackageAll() = doModuleTest("ModuleMissingFromPackageAll")

    fun testModulePresentInPackageAll() = doModuleTest("ModulePresentInPackageAll")

    fun testModuleMissingFromPackageAllFix() = doModuleFixTest("ModuleMissingFromPackageAllFix")

    fun testModuleMissingFromPackageAllFix_NoAll() = doModuleFixTest("ModuleMissingFromPackageAllFix_NoAll")

    fun testModuleMissingFromPackageAllFix_WithExistingImportFromOtherModule() {
        val testName = "ModuleMissingFromPackageAllFix_WithExistingImportFromOtherModule"

        myFixture.configureByFiles(
            "inspections/PyMissingInDunderAllInspection/$testName/__init__.py",
            "inspections/PyMissingInDunderAllInspection/$testName/client.py",
            "inspections/PyMissingInDunderAllInspection/$testName/other.py",
        )

        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        // Ensure the quick-fix is invoked in the context of the module that
        // declares the missing symbol. ModCommand-based quick-fixes rely on
        // the currently opened editor file as the context for the problem
        // element, so we must open client.py before collecting and applying
        // fixes.
        val moduleFile = myFixture.findFileInTempDir(
            "inspections/PyMissingInDunderAllInspection/$testName/client.py",
        )
        myFixture.openFileInEditor(moduleFile!!)

        val fixes = myFixture.getAllQuickFixes()
        fixes
            .filter { it.familyName == "Add to __all__" }
            .forEach { myFixture.launchAction(it) }

        val initFile = myFixture.findFileInTempDir(
            "inspections/PyMissingInDunderAllInspection/$testName/__init__.py",
        )
        myFixture.openFileInEditor(initFile!!)
        myFixture.checkResultByFile(
            "inspections/PyMissingInDunderAllInspection/${testName}_after/__init__.py",
        )
    }

    fun testAllowlistedTestFunction() {
        val testName = getTestName(false)
        myFixture.configureByFile("inspections/PyMissingInDunderAllInspection/$testName/__init__.py")

        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fixes = myFixture.getAllQuickFixes()
        assertTrue("Add to __all__ intention should not be offered for allowlisted symbols", fixes.none {
            it.familyName == "Add to __all__"
        })
    }

    fun testAllowlistedTestPackage() {
        val testName = getTestName(false)
        myFixture.configureByFile("inspections/PyMissingInDunderAllInspection/$testName/__init__.py")

        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fixes = myFixture.getAllQuickFixes()
        assertTrue("Add to __all__ intention should not be offered for symbols in allowlisted packages", fixes.none {
            it.familyName == "Add to __all__"
        })
    }

    fun testAllowlistedTestModule() {
        myFixture.configureByFiles(
            "inspections/PyMissingInDunderAllInspection/AllowlistedTestModule/__init__.py",
            "inspections/PyMissingInDunderAllInspection/AllowlistedTestModule/test_helpers.py",
        )

        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fixes = myFixture.getAllQuickFixes()
        assertTrue("Add to __all__ intention should not be offered for symbols in allowlisted modules", fixes.none {
            it.familyName == "Add to __all__"
        })
    }

    private fun doTest(applyFix: Boolean = false) {
        val testName = getTestName(false)
        myFixture.configureByFile("inspections/PyMissingInDunderAllInspection/$testName/__init__.py")

        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        if (applyFix) {
            val fixes = myFixture.getAllQuickFixes()
            fixes
                .filter { it.familyName == "Add to __all__" }
                .forEach { myFixture.launchAction(it) }

            myFixture.checkResultByFile("inspections/PyMissingInDunderAllInspection/${testName}_after/__init__.py")
        }
    }

    private fun doModuleTest(testName: String) {
        myFixture.configureByFiles(
            "inspections/PyMissingInDunderAllInspection/$testName/__init__.py",
            "inspections/PyMissingInDunderAllInspection/$testName/module.py",
        )

        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    private fun doModuleFixTest(testName: String) {
        myFixture.configureByFiles(
            "inspections/PyMissingInDunderAllInspection/$testName/__init__.py",
            "inspections/PyMissingInDunderAllInspection/$testName/module.py",
        )

        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        // Ensure the quick-fix is invoked in the context of the module that
        // declares the missing symbol. ModCommand-based quick-fixes rely on
        // the currently opened editor file as the context for the problem
        // element, so we must open module.py before collecting and applying
        // fixes.
        val moduleFile = myFixture.findFileInTempDir(
            "inspections/PyMissingInDunderAllInspection/$testName/module.py",
        )
        myFixture.openFileInEditor(moduleFile!!)

        val fixes = myFixture.getAllQuickFixes()
        fixes
            .filter { it.familyName == "Add to __all__" }
            .forEach { myFixture.launchAction(it) }

        // After applying the quick-fix, open the package __init__.py and
        // compare it against the expected _after version to validate the
        // cross-file update.
        val initFile = myFixture.findFileInTempDir("inspections/PyMissingInDunderAllInspection/$testName/__init__.py")
        myFixture.openFileInEditor(initFile!!)

        // Debug helper: log the transformed __init__.py contents when a
        // comparison failure occurs, to make it easier to align formatting
        // (e.g. blank lines) with the expected _after file.
        println("[DEBUG_LOG] Transformed __init__.py for $testName:\n" + myFixture.editor.document.text)

        myFixture.checkResultByFile(
            "inspections/PyMissingInDunderAllInspection/${testName}_after/__init__.py",
        )
    }
}