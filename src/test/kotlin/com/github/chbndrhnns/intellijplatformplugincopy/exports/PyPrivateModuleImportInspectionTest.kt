package com.github.chbndrhnns.intellijplatformplugincopy.exports

import fixtures.TestBase
import fixtures.doMultiFileInspectionTest

class PyPrivateModuleImportInspectionTest : TestBase() {

    fun testUseExportedSymbolFromPackageQuickFix() {
        val path = "inspections/PyPrivateModuleImportInspection/UseExportedSymbolFromPackage"
        myFixture.doMultiFileInspectionTest(
            files = listOf("$path/mypackage/__init__.py", "$path/mypackage/_lib.py", "$path/cli.py"),
            inspection = PyPrivateModuleImportInspection::class.java,
            targetFile = "$path/cli.py",
            fixFamilyName = "Use exported symbol from package instead of private module",
            resultFileToCheck = "$path/cli.py",
            expectedResultFile = "$path/cli_after.py",
            checkHighlighting = false
        )
    }

    fun testUseExportedSymbolFromPackageQuickFix_OnWholeStatement() {
        val path = "inspections/PyPrivateModuleImportInspection/UseExportedSymbolFromPackage_OnWholeStatement"
        myFixture.doMultiFileInspectionTest(
            files = listOf("$path/mypackage/__init__.py", "$path/mypackage/_lib.py", "$path/cli.py"),
            inspection = PyPrivateModuleImportInspection::class.java,
            targetFile = "$path/cli.py",
            fixFamilyName = "Use exported symbol from package instead of private module",
            resultFileToCheck = "$path/cli.py",
            expectedResultFile = "$path/cli_after.py",
            checkHighlighting = false
        )
    }

    fun testMakeSymbolPublicAndUseExportedSymbolQuickFix() {
        val path = "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol"
        myFixture.doMultiFileInspectionTest(
            files = listOf("$path/mypackage/__init__.py", "$path/mypackage/_lib.py", "$path/cli.py"),
            inspection = PyPrivateModuleImportInspection::class.java,
            targetFile = "$path/cli.py",
            fixFamilyName = "Make symbol public and import from package",
            expectedResultFiles = mapOf(
                "$path/cli.py" to "$path/cli_after.py",
                "$path/mypackage/__init__.py" to "$path/mypackage/__init___after.py"
            ),
            checkHighlighting = false
        )
    }

    fun testMakeSymbolPublicAndUseExportedSymbolQuickFix_MultipleSites() {
        val path = "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_MultipleSites"
        myFixture.doMultiFileInspectionTest(
            files = listOf(
                "$path/mypackage/__init__.py",
                "$path/mypackage/_lib.py",
                "$path/cli.py",
                "$path/other_consumer.py"
            ),
            inspection = PyPrivateModuleImportInspection::class.java,
            targetFile = "$path/cli.py",
            fixFamilyName = "Make symbol public and import from package",
            expectedResultFiles = mapOf(
                "$path/cli.py" to "$path/cli_after.py",
                "$path/other_consumer.py" to "$path/other_consumer_after.py",
                "$path/mypackage/__init__.py" to "$path/mypackage/__init___after.py"
            ),
            checkHighlighting = false
        )
    }

    fun testMakeSymbolPublicAndUseExportedSymbolQuickFix_SkipPackageRefs() {
        val path = "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_SkipPackageRefs"
        myFixture.doMultiFileInspectionTest(
            files = listOf(
                "$path/mypackage/__init__.py",
                "$path/mypackage/_lib.py",
                "$path/mypackage/inside.py",
                "$path/cli.py"
            ),
            inspection = PyPrivateModuleImportInspection::class.java,
            targetFile = "$path/cli.py",
            fixFamilyName = "Make symbol public and import from package",
            expectedResultFiles = mapOf(
                "$path/cli.py" to "$path/cli_after.py",
                "$path/mypackage/inside.py" to "$path/mypackage/inside_after.py",
                "$path/mypackage/__init__.py" to "$path/mypackage/__init___after.py"
            ),
            checkHighlighting = false
        )
    }

    fun testQuickFixNotOfferedInInit() {
        myFixture.configureByFiles(
            "inspections/PyPrivateModuleImportInspection/DoNotOfferInInit/mypackage/__init__.py",
            "inspections/PyPrivateModuleImportInspection/DoNotOfferInInit/mypackage/_lib.py",
        )

        val initFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/DoNotOfferInInit/mypackage/__init__.py",
        )
        myFixture.openFileInEditor(initFile!!)

        myFixture.enableInspections(PyPrivateModuleImportInspection::class.java)
        myFixture.doHighlighting()

        val fixes = myFixture.getAllQuickFixes()
        assertTrue(
            "Use-exported-symbol quick-fix should not be offered in __init__.py where the re-export happens",
            fixes.none { it.familyName == "Use exported symbol from package instead of private module" },
        )
    }

    fun testQuickFixNotOfferedInSamePackage() {
        myFixture.configureByFiles(
            "inspections/PyPrivateModuleImportInspection/DoNotOfferInSamePackage/myp/__init__.py",
            "inspections/PyPrivateModuleImportInspection/DoNotOfferInSamePackage/myp/_one.py",
            "inspections/PyPrivateModuleImportInspection/DoNotOfferInSamePackage/myp/two.py",
        )

        val twoFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/DoNotOfferInSamePackage/myp/two.py",
        )
        myFixture.openFileInEditor(twoFile!!)

        myFixture.enableInspections(PyPrivateModuleImportInspection::class.java)
        myFixture.doHighlighting()

        val fixes = myFixture.getAllQuickFixes()
        assertTrue(
            "Quick-fix should not be offered when importing from private module within the same package",
            fixes.none {
                it.familyName == "Use exported symbol from package instead of private module" ||
                        it.familyName == "Make symbol public and import from package"
            },
        )
    }

    fun testQuickFixNotOfferedInPrivateChildPackage() {
        myFixture.configureByFiles(
            "inspections/PyPrivateModuleImportInspection/DoNotOfferInPrivateChildPackage/mypackage/__init__.py",
            "inspections/PyPrivateModuleImportInspection/DoNotOfferInPrivateChildPackage/mypackage/_lib.py",
            "inspections/PyPrivateModuleImportInspection/DoNotOfferInPrivateChildPackage/mypackage/_child/consumer.py",
        )

        val consumerFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/DoNotOfferInPrivateChildPackage/mypackage/_child/consumer.py",
        )
        myFixture.openFileInEditor(consumerFile!!)

        myFixture.enableInspections(PyPrivateModuleImportInspection::class.java)
        myFixture.doHighlighting()

        val fixes = myFixture.getAllQuickFixes()
        assertTrue(
            "Quick-fix should not be offered when importing from private module in a private child package",
            fixes.none {
                it.familyName == "Use exported symbol from package instead of private module" ||
                        it.familyName == "Make symbol public and import from package"
            },
        )
    }
}
