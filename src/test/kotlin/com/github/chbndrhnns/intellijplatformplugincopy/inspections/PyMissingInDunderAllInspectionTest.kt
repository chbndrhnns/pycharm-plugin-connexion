package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.intellij.testFramework.PsiTestUtil
import fixtures.TestBase
import fixtures.doInspectionTest
import fixtures.doMultiFileInspectionTest

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
        val basePath = "inspections/PyMissingInDunderAllInspection/$testName"

        myFixture.doMultiFileInspectionTest(
            files = listOf("$basePath/__init__.py", "$basePath/client.py", "$basePath/other.py"),
            inspection = PyMissingInDunderAllInspection::class.java,
            targetFile = "$basePath/client.py",
            checkHighlighting = false,
            checkWeakWarnings = true,
            fixFamilyName = "Add to __all__",
            resultFileToCheck = "$basePath/__init__.py",
            expectedResultFile = "inspections/PyMissingInDunderAllInspection/${testName}_after/__init__.py"
        )
    }

    fun testPrivateModuleMissingFromPackageAllFix_NoAll() {
        val testName = "PrivateModuleMissingFromPackageAllFix_NoAll"
        val basePath = "inspections/PyMissingInDunderAllInspection/$testName"

        myFixture.doMultiFileInspectionTest(
            files = listOf("$basePath/__init__.py", "$basePath/_module.py"),
            inspection = PyMissingInDunderAllInspection::class.java,
            targetFile = "$basePath/_module.py",
            checkHighlighting = false,
            checkWeakWarnings = false,
            fixFamilyName = "Add to __all__",
            resultFileToCheck = "$basePath/__init__.py",
            expectedResultFile = "inspections/PyMissingInDunderAllInspection/${testName}_after/__init__.py"
        )
    }

    fun testPublicModuleMissingFromPackageAllFix_NoAll() {
        val testName = "PublicModuleMissingFromPackageAllFix_NoAll"
        val basePath = "inspections/PyMissingInDunderAllInspection/$testName"

        myFixture.doMultiFileInspectionTest(
            files = listOf("$basePath/__init__.py", "$basePath/module.py"),
            inspection = PyMissingInDunderAllInspection::class.java,
            targetFile = "$basePath/module.py",
            checkHighlighting = false,
            checkWeakWarnings = true,
            fixFamilyName = "Add to __all__",
            resultFileToCheck = "$basePath/__init__.py",
            expectedResultFile = "inspections/PyMissingInDunderAllInspection/${testName}_after/__init__.py"
        )
    }

    fun testNewType() {
        val modFile = myFixture.addFileToProject(
            "pkg/_mod.py", """
            from typing import NewType
            MyStr = NewType("MyStr", str)
        """.trimIndent()
        )

        myFixture.addFileToProject(
            "pkg/__init__.py", """
            from ._mod import MyStr
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(modFile.virtualFile)
        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)

        val fix = myFixture.getAllQuickFixes().find { it.familyName == "Add to __all__" }
        assertTrue("Fix 'Add to __all__' not found", fix != null)

        myFixture.launchAction(fix!!)

        myFixture.checkResult(
            "pkg/__init__.py", """
            __all__ = ["MyStr"]

            from ._mod import MyStr
        """.trimIndent(), true
        )
    }

    fun testTopLevelAttribute() {
        val modFile = myFixture.addFileToProject(
            "pkg/_mod.py", """
            my_attr = "abc"
        """.trimIndent()
        )

        myFixture.addFileToProject(
            "pkg/__init__.py", """
            from ._mod import my_attr
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(modFile.virtualFile)
        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)

        val fix = myFixture.getAllQuickFixes().find { it.familyName == "Add to __all__" }
        assertTrue("Fix 'Add to __all__' not found", fix != null)

        myFixture.launchAction(fix!!)

        myFixture.checkResult(
            "pkg/__init__.py", """
            __all__ = ["my_attr"]

            from ._mod import my_attr
        """.trimIndent(), true
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

    fun testIgnoreConftest() {
        myFixture.configureByFiles(
            "inspections/PyMissingInDunderAllInspection/IgnoreConftest/__init__.py",
            "inspections/PyMissingInDunderAllInspection/IgnoreConftest/conftest.py",
        )

        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fixes = myFixture.getAllQuickFixes()
        assertTrue(
            "Add to __all__ intention should not be offered for pytest conftest.py files",
            fixes.none { it.familyName == "Add to __all__" }
        )
    }

    fun testSkipLibraryAndStdlibModules() {
        val testName = "SkipLibraries"

        myFixture.copyDirectoryToProject(
            "inspections/PyMissingInDunderAllInspection/$testName",
            "inspections/PyMissingInDunderAllInspection/$testName",
        )

        val libRoot = myFixture.findFileInTempDir(
            "inspections/PyMissingInDunderAllInspection/$testName/lib_pkg",
        )
        assertNotNull("Library root should exist", libRoot)

        // Mark lib_pkg as an excluded root so that its files are not part of
        // the module content and therefore get skipped by the
        // inspection's isUserCodeFile() guard.
        PsiTestUtil.addExcludedRoot(myFixture.module, libRoot)

        // 1) Project module should still be inspected and report the
        //    missing export from project_pkg._module (private module only).
        myFixture.configureByFile(
            "inspections/PyMissingInDunderAllInspection/$testName/project_pkg/_module.py",
        )

        myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
        val projectInfos = myFixture.doHighlighting()

        assertTrue(
            "Expected at least one missing-__all__ problem in project code for private module",
            projectInfos.any { it.description?.contains("not exported in package __all__") == true },
        )

        // 2) Library module should *not* be inspected at all – there should
        //    be no missing-__all__ problems when opening lib_pkg/module.py.
        myFixture.configureByFile(
            "inspections/PyMissingInDunderAllInspection/$testName/lib_pkg/module.py",
        )

        val libraryInfos = myFixture.doHighlighting()
        assertTrue(
            "No missing-__all__ problems should be reported for library code",
            libraryInfos.none { it.description?.contains("not exported in package __all__") == true },
        )
    }

    private fun doTest(applyFix: Boolean = false) {
        val testName = getTestName(false)
        val basePath = "inspections/PyMissingInDunderAllInspection"

        myFixture.doInspectionTest(
            testFile = "$basePath/$testName/__init__.py",
            inspection = PyMissingInDunderAllInspection::class.java,
            fixFamilyName = if (applyFix) "Add to __all__" else null,
            expectedResultFile = if (applyFix) "$basePath/${testName}_after/__init__.py" else null,
            checkHighlighting = true
        )
    }

    private fun doModuleTest(testName: String) {
        val basePath = "inspections/PyMissingInDunderAllInspection"
        myFixture.doMultiFileInspectionTest(
            files = listOf("$basePath/$testName/__init__.py", "$basePath/$testName/module.py"),
            inspection = PyMissingInDunderAllInspection::class.java,
            checkHighlighting = true,
            checkWeakWarnings = false
        )
    }

    private fun doModuleFixTest(testName: String) {
        val basePath = "inspections/PyMissingInDunderAllInspection"

        myFixture.doMultiFileInspectionTest(
            files = listOf("$basePath/$testName/__init__.py", "$basePath/$testName/module.py"),
            inspection = PyMissingInDunderAllInspection::class.java,
            targetFile = "$basePath/$testName/module.py",
            checkHighlighting = false,
            checkWeakWarnings = true,
            fixFamilyName = "Add to __all__",
            resultFileToCheck = "$basePath/$testName/__init__.py",
            expectedResultFile = "$basePath/${testName}_after/__init__.py"
        )
    }
}
