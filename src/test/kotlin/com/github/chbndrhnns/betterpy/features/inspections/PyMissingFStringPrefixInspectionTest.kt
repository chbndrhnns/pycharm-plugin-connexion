package com.github.chbndrhnns.betterpy.features.inspections

import fixtures.TestBase
import fixtures.doInspectionTest

class PyMissingFStringPrefixInspectionTest : TestBase() {

    private val inspection = PyMissingFStringPrefixInspection::class.java
    private val fixName = "BetterPy: Convert to f-string"

    fun testSimple() {
        myFixture.doInspectionTest(
            "inspections/PyMissingFStringPrefixInspection/Simple/test.py",
            inspection,
            fixName,
            "inspections/PyMissingFStringPrefixInspection/Simple/test_after.py",
            checkWeakWarnings = true
        )
    }

    fun testAlreadyFString() {
        myFixture.doInspectionTest(
            "inspections/PyMissingFStringPrefixInspection/AlreadyFString/test.py",
            inspection,
            checkWeakWarnings = true
        )
    }

    fun testNoPlaceholders() {
        myFixture.doInspectionTest(
            "inspections/PyMissingFStringPrefixInspection/NoPlaceholders/test.py",
            inspection,
            checkWeakWarnings = true
        )
    }

    fun testByteString() {
        myFixture.doInspectionTest(
            "inspections/PyMissingFStringPrefixInspection/ByteString/test.py",
            inspection,
            checkWeakWarnings = true
        )
    }
}
