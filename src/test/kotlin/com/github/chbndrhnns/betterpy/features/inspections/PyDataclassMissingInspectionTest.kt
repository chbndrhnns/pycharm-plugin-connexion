package com.github.chbndrhnns.betterpy.features.inspections

import fixtures.TestBase

class PyDataclassMissingInspectionTest : TestBase() {

    fun testDataclassMissingOnSubclass() {
        myFixture.configureByText(
            "test.py",
            """
            import dataclasses
            from dataclasses import dataclass

            @dataclass
            class Base:
                x: int

            class <warning descr="Subclass of a dataclass should also be decorated with @dataclass">Derived</warning>(Base):
                y: int

            @dataclass
            class DerivedCorrect(Base):
                z: int

            class Normal:
                pass

            class DerivedNormal(Normal):
                pass
            """.trimIndent()
        )
        myFixture.enableInspections(PyDataclassMissingInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testQuickFix_AddDataclass() {
        myFixture.configureByText(
            "test.py",
            """
            from dataclasses import dataclass

            @dataclass
            class Base:
                x: int

            class <warning descr="Subclass of a dataclass should also be decorated with @dataclass"><caret>Derived</warning>(Base):
                y: int
            """.trimIndent()
        )
        myFixture.enableInspections(PyDataclassMissingInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fix = myFixture.findSingleIntention("BetterPy: Add @dataclass decorator")
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
            from dataclasses import dataclass

            @dataclass
            class Base:
                x: int

            @dataclass
            class Derived(Base):
                y: int
            """.trimIndent()
        )
    }

    fun testQuickFix_AddDataclassWithImport() {
        myFixture.configureByText(
            "test.py",
            """
            import dataclasses

            @dataclasses.dataclass
            class Base:
                x: int

            class <warning descr="Subclass of a dataclass should also be decorated with @dataclass"><caret>Derived</warning>(Base):
                y: int
            """.trimIndent()
        )
        myFixture.enableInspections(PyDataclassMissingInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fix = myFixture.findSingleIntention("BetterPy: Add @dataclass decorator")
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
            import dataclasses

            @dataclasses.dataclass
            class Base:
                x: int

            @dataclasses.dataclass
            class Derived(Base):
                y: int
            """.trimIndent()
        )
    }
}
