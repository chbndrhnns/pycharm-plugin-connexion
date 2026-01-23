package com.github.chbndrhnns.betterpy.features.exports

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

//
class PyMissingInDunderAllSettingsToggleTest : TestBase() {

    fun testInspectionDisabledViaSettings() {
        withPluginSettings({ enablePyMissingInDunderAllInspection = false }) {
            myFixture.configureByFile("inspections/PyMissingInDunderAllInspection/SettingsDisabled/__init__.py")

            myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
            myFixture.checkHighlighting(true, false, false)
        }
    }

    fun testInspectionEnabledViaSettings() {
        withPluginSettings({ enablePyMissingInDunderAllInspection = true }) {
            myFixture.configureByFile("inspections/PyMissingInDunderAllInspection/SettingsEnabled/__init__.py")

            myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
            myFixture.checkHighlighting(true, false, false)
        }
    }
}
