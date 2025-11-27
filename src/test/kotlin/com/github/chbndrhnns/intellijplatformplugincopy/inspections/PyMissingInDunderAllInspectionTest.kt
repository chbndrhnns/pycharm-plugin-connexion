package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import fixtures.TestBase

class PyMissingInDunderAllInspectionTest : TestBase() {

    fun testMissingInAll() = doTest()

    fun testPresentInAll() = doTest()

    fun testNoDunderAll() = doTest()

    fun testPrivateSymbol() = doTest()

    fun testNonSequenceAllIsIgnored() = doTest()

    fun testAddToAllFix() = doTest(applyFix = true)

    fun testModuleMissingFromPackageAll() = doModuleTest("ModuleMissingFromPackageAll")

    fun testModulePresentInPackageAll() = doModuleTest("ModulePresentInPackageAll")

    fun testModuleMissingFromPackageAllFix() = doModuleFixTest("ModuleMissingFromPackageAllFix")

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
        myFixture.checkResultByFile(
            "inspections/PyMissingInDunderAllInspection/${testName}_after/__init__.py",
        )
    }
}