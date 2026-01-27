package com.github.chbndrhnns.betterpy.features.inspections

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase
import fixtures.doInspectionTest

class PyOverriddenMethodMissingTypeAnnotationsInspectionTest : TestBase() {

    private val inspection = PyOverriddenMethodMissingTypeAnnotationsInspection::class.java
    private val fixName = "BetterPy: Copy type annotations from parent"

    fun testHighlightsMissingAnnotationsAndFix() {
        myFixture.doInspectionTest(
            "inspections/PyOverriddenMethodMissingTypeAnnotationsInspection/Partial/test.py",
            inspection,
            fixName,
            "inspections/PyOverriddenMethodMissingTypeAnnotationsInspection/Partial/test_after.py",
            checkWeakWarnings = true
        )
    }

    fun testNoWarningWhenAnnotationsMatch() {
        myFixture.doInspectionTest(
            "inspections/PyOverriddenMethodMissingTypeAnnotationsInspection/Full/test.py",
            inspection,
            checkWeakWarnings = true
        )
    }

    fun testNoWeakWarningWhenAnnotationsDifferButFixAvailable() {
        myFixture.doInspectionTest(
            "inspections/PyOverriddenMethodMissingTypeAnnotationsInspection/Mismatch/test.py",
            inspection,
            fixName,
            "inspections/PyOverriddenMethodMissingTypeAnnotationsInspection/Mismatch/test_after.py",
            checkWeakWarnings = true,
            dialogOk = true
        )
    }

    fun testInspectionDisabled() {
        withPluginSettings({ enableOverriddenMethodMissingTypeAnnotationsInspection = false }) {
            myFixture.doInspectionTest(
                "inspections/PyOverriddenMethodMissingTypeAnnotationsInspection/Disabled/test.py",
                inspection,
                checkWeakWarnings = true
            )
        }
    }
}
