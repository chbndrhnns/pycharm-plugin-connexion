package com.jetbrains.python.inspections;

import fixtures.TestBase;

public class PyMissingInDunderAllInspectionTest extends TestBase {

    public void testMissingInAll() {
        doTest();
    }

    public void testPresentInAll() {
        doTest();
    }

    public void testNoDunderAll() {
        doTest();
    }

    public void testPrivateSymbol() {
        doTest();
    }

    /**
     * Private module (file starts with underscore) inside a package with __all__,
     * where the symbol is NOT exported from the package's __all__.
     */
    public void testPrivateModuleNotExportedFromPackage() {
        doTest();
    }

    /**
     * Private module inside a package with __all__, where the symbol *is*
     * exported from the package's __all__. No warning is expected.
     */
    public void testPrivateModuleExportedFromPackage() {
        doTest();
    }

    public void testAddToAllFix() {
        doTest(); // Checks quickfix application for module-level __all__.
    }

    private void doTest() {
        String testName = getTestName(false);
        String relativePath;
        if ("PrivateModuleNotExportedFromPackage".equals(testName)) {
            relativePath = "inspections/PyMissingInDunderAllInspection/pkg/_private_module_not_exported.py";
        } else if ("PrivateModuleExportedFromPackage".equals(testName)) {
            relativePath = "inspections/PyMissingInDunderAllInspection/pkg/_private_module_exported.py";
        } else {
            relativePath = "inspections/PyMissingInDunderAllInspection/" + testName + ".py";
        }

        myFixture.configureByFile(relativePath);
        myFixture.enableInspections(PyMissingInDunderAllInspection.class);
        myFixture.checkHighlighting(true, false, false);

        // Apply quickfix if available (heuristic for test name ending in 'Fix').
        // This only exercises the module-level quick fix; there is currently no
        // package-level quick fix wired for the private-module / package __all__ case.
        if (getTestName(false).contains("Fix")) {
            myFixture.getAllQuickFixes().forEach(fix -> {
                if ("Add to __all__".equals(fix.getFamilyName())) {
                    myFixture.launchAction(fix);
                }
            });
            myFixture.checkResultByFile("inspections/PyMissingInDunderAllInspection/" + getTestName(false) + "_after.py");
        }
    }
}
