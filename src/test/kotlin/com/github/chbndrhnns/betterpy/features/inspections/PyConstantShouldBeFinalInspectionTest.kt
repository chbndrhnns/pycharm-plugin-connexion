package com.github.chbndrhnns.betterpy.features.inspections

import fixtures.TestBase
import fixtures.doInspectionTest

class PyConstantShouldBeFinalInspectionTest : TestBase() {

    private val inspection = PyConstantShouldBeFinalInspection::class.java
    private val fixName = "BetterPy: Wrap constant type in Final"

    fun testLiteral() {
        myFixture.doInspectionTest(
            "inspections/PyConstantShouldBeFinalInspection/Literal/test.py",
            inspection,
            fixName,
            "inspections/PyConstantShouldBeFinalInspection/Literal/test_after.py"
        )
    }

    fun testAnnotated() {
        myFixture.doInspectionTest(
            "inspections/PyConstantShouldBeFinalInspection/Annotated/test.py",
            inspection,
            fixName,
            "inspections/PyConstantShouldBeFinalInspection/Annotated/test_after.py"
        )
    }

    fun testAlreadyFinal() {
        myFixture.doInspectionTest(
            "inspections/PyConstantShouldBeFinalInspection/AlreadyFinal/test.py",
            inspection
        )
    }

    fun testTypingExtensions() {
        myFixture.doInspectionTest(
            "inspections/PyConstantShouldBeFinalInspection/TypingExtensions/test.py",
            inspection,
            fixName,
            "inspections/PyConstantShouldBeFinalInspection/TypingExtensions/test_after.py"
        )
    }

    fun testNoneInitializer() {
        myFixture.doInspectionTest(
            "inspections/PyConstantShouldBeFinalInspection/NoneInitializer/test.py",
            inspection,
            fixName,
            "inspections/PyConstantShouldBeFinalInspection/NoneInitializer/test_after.py"
        )
    }

    fun testEnumMembers() {
        myFixture.doInspectionTest(
            "inspections/PyConstantShouldBeFinalInspection/EnumMembers/test.py",
            inspection
        )
    }
}
