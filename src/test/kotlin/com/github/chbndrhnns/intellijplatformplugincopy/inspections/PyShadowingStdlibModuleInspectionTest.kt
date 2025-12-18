package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import fixtures.TestBase

class PyShadowingStdlibModuleInspectionTest : TestBase() {

    private val inspection = PyShadowingStdlibModuleInspection::class.java

    fun testShadowing() {
        myFixture.enableInspections(inspection)
        myFixture.testHighlighting(true, false, false, "inspections/PyShadowingStdlibModuleInspection/Shadowing/os.py")
    }

    fun testNoShadowing() {
        myFixture.enableInspections(inspection)
        myFixture.testHighlighting(
            true,
            false,
            false,
            "inspections/PyShadowingStdlibModuleInspection/NoShadowing/my_script.py"
        )
    }
}
