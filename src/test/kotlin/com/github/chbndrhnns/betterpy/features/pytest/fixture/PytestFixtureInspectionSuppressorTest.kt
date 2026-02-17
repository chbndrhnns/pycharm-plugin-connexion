package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.jetbrains.python.inspections.PyMethodOverridingInspection
import fixtures.TestBase

class PytestFixtureInspectionSuppressorTest : TestBase() {

    fun testSuppressesMethodOverridingForFixture() {
        myFixture.enableInspections(PyMethodOverridingInspection::class.java)
        myFixture.configureByText(
            "test_fixture.py", """
            import pytest
            
            
            @pytest.fixture
            def myf():
                pass
            
            
            class Test:
                @pytest.fixture
                def myf(self, myf):
                    return myf
            
                def test_(self):
                    assert True
        """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        val methodOverridingWarnings = highlights.filter {
            it.description?.contains("Signature of method") == true &&
                    it.description?.contains("does not match signature") == true
        }

        assertTrue("Should not have method overriding warnings for fixtures", methodOverridingWarnings.isEmpty())
    }

    fun testDoesNotSuppressForNonFixture() {
        myFixture.enableInspections(PyMethodOverridingInspection::class.java)
        myFixture.configureByText(
            "test_non_fixture.py", """
            class Base:
                def method(self):
                    pass
            
            
            class Derived(Base):
                def method(self, extra_param):
                    pass
        """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        val methodOverridingWarnings = highlights.filter {
            it.description?.contains("Signature of method") == true &&
                    it.description?.contains("does not match signature") == true
        }

        assertFalse("Should have method overriding warning for non-fixtures", methodOverridingWarnings.isEmpty())
    }
}
