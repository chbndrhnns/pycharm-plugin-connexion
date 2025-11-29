package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import fixtures.TestBase

class PyPrivateModuleImportInspectionTest : TestBase() {

    fun testUseExportedSymbolFromPackageQuickFix() {
        myFixture.configureByFiles(
            "inspections/PyPrivateModuleImportInspection/UseExportedSymbolFromPackage/mypackage/__init__.py",
            "inspections/PyPrivateModuleImportInspection/UseExportedSymbolFromPackage/mypackage/_lib.py",
            "inspections/PyPrivateModuleImportInspection/UseExportedSymbolFromPackage/cli.py",
        )

        // Ensure the quick-fix is invoked in the context of the file that
        // contains the import statement (cli.py). ModCommand-based
        // quick-fixes rely on the currently opened editor file as the context
        // for the problem element, so we must open cli.py before collecting
        // and applying fixes.
        val cliFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/UseExportedSymbolFromPackage/cli.py",
        )
        myFixture.openFileInEditor(cliFile!!)

        myFixture.enableInspections(PyPrivateModuleImportInspection::class.java)
        myFixture.doHighlighting()

        val fixes = myFixture.getAllQuickFixes()
        fixes
            .filter { it.familyName == "Use exported symbol from package instead of private module" }
            .forEach { myFixture.launchAction(it) }

        myFixture.checkResultByFile(
            "inspections/PyPrivateModuleImportInspection/UseExportedSymbolFromPackage/cli_after.py",
        )
    }

    fun testMakeSymbolPublicAndUseExportedSymbolQuickFix() {
        myFixture.configureByFiles(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol/mypackage/__init__.py",
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol/mypackage/_lib.py",
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol/cli.py",
        )

        val cliFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol/cli.py",
        )
        myFixture.openFileInEditor(cliFile!!)

        myFixture.enableInspections(PyPrivateModuleImportInspection::class.java)
        myFixture.doHighlighting()

        val fixes = myFixture.getAllQuickFixes()
        fixes
            .filter { it.familyName == "Make symbol public and import from package" }
            .forEach { myFixture.launchAction(it) }

        myFixture.checkResultByFile(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol/cli_after.py",
        )

        val initFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol/mypackage/__init__.py",
        )
        myFixture.openFileInEditor(initFile!!)
        myFixture.checkResultByFile(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol/mypackage/__init___after.py",
        )
    }

    fun testMakeSymbolPublicAndUseExportedSymbolQuickFix_MultipleSites() {
        myFixture.configureByFiles(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_MultipleSites/mypackage/__init__.py",
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_MultipleSites/mypackage/_lib.py",
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_MultipleSites/cli.py",
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_MultipleSites/other_consumer.py",
        )

        val cliFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_MultipleSites/cli.py",
        )
        myFixture.openFileInEditor(cliFile!!)

        myFixture.enableInspections(PyPrivateModuleImportInspection::class.java)
        myFixture.doHighlighting()

        val fixes = myFixture.getAllQuickFixes()
        fixes
            .filter { it.familyName == "Make symbol public and import from package" }
            .forEach { myFixture.launchAction(it) }

        myFixture.checkResultByFile(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_MultipleSites/cli_after.py",
        )

        val otherFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_MultipleSites/other_consumer.py",
        )
        myFixture.openFileInEditor(otherFile!!)
        myFixture.checkResultByFile(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_MultipleSites/other_consumer_after.py",
        )

        val initFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_MultipleSites/mypackage/__init__.py",
        )
        myFixture.openFileInEditor(initFile!!)
        myFixture.checkResultByFile(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_MultipleSites/mypackage/__init___after.py",
        )
    }

    fun testMakeSymbolPublicAndUseExportedSymbolQuickFix_SkipPackageRefs() {
        myFixture.configureByFiles(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_SkipPackageRefs/mypackage/__init__.py",
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_SkipPackageRefs/mypackage/_lib.py",
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_SkipPackageRefs/mypackage/inside.py",
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_SkipPackageRefs/cli.py",
        )

        val cliFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_SkipPackageRefs/cli.py",
        )
        myFixture.openFileInEditor(cliFile!!)

        myFixture.enableInspections(PyPrivateModuleImportInspection::class.java)
        myFixture.doHighlighting()

        val fixes = myFixture.getAllQuickFixes()
        fixes
            .filter { it.familyName == "Make symbol public and import from package" }
            .forEach { myFixture.launchAction(it) }

        myFixture.checkResultByFile(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_SkipPackageRefs/cli_after.py",
        )

        val insideFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_SkipPackageRefs/mypackage/inside.py",
        )
        myFixture.openFileInEditor(insideFile!!)
        myFixture.checkResultByFile(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_SkipPackageRefs/mypackage/inside_after.py",
        )

        val initFile = myFixture.findFileInTempDir(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_SkipPackageRefs/mypackage/__init__.py",
        )
        myFixture.openFileInEditor(initFile!!)
        myFixture.checkResultByFile(
            "inspections/PyPrivateModuleImportInspection/MakeSymbolPublicAndUseExportedSymbol_SkipPackageRefs/mypackage/__init___after.py",
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
}
