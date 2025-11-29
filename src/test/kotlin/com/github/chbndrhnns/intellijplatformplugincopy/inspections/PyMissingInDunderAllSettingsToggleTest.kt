package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class PyMissingInDunderAllSettingsToggleTest : TestBase() {

    fun testInspectionDisabledViaSettings() {
        withPluginSettings({ it.copy(enablePyMissingInDunderAllInspection = false) }) {
            myFixture.configureByFile("inspections/PyMissingInDunderAllInspection/MissingInAll/__init__.py")

            myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
            // When disabled via settings, the inspection should not report any problems.
            myFixture.checkHighlighting(true, false, false)
        }
    }

    fun testInspectionEnabledViaSettings() {
        withPluginSettings({ it.copy(enablePyMissingInDunderAllInspection = true) }) {
            myFixture.configureByFile("inspections/PyMissingInDunderAllInspection/MissingInAll/__init__.py")

            myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
            myFixture.checkHighlighting(true, false, false)
        }
    }
}
