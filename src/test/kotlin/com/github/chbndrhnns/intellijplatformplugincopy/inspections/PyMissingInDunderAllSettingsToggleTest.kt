package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import fixtures.TestBase

class PyMissingInDunderAllSettingsToggleTest : TestBase() {

    fun testInspectionDisabledViaSettings() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(old.copy(enablePyMissingInDunderAllInspection = false))

            myFixture.configureByFile("inspections/PyMissingInDunderAllInspection/MissingInAll/__init__.py")

            myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
            // When disabled via settings, the inspection should not report any problems.
            myFixture.checkHighlighting(true, false, false)
        } finally {
            svc.loadState(old)
        }
    }

    fun testInspectionEnabledViaSettings() {
        val svc = PluginSettingsState.instance()
        val old = svc.state
        try {
            svc.loadState(old.copy(enablePyMissingInDunderAllInspection = true))

            myFixture.configureByFile("inspections/PyMissingInDunderAllInspection/MissingInAll/__init__.py")

            myFixture.enableInspections(PyMissingInDunderAllInspection::class.java)
            myFixture.checkHighlighting(true, false, false)
        } finally {
            svc.loadState(old)
        }
    }
}
