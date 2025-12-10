package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import fixtures.TestBase

class PyAbstractMethodNotImplementedInChildInspectionTest : TestBase() {

    fun testInspectionReportsMissingMethod() {
        myFixture.configureByText(
            "a.py",
            """
            import abc
            
            class <warning descr="Child class 'Child' is missing implementation for abstract methods: foo">Base</warning>(abc.ABC):
                @abc.abstractmethod
                def foo(self):
                    raise NotImplementedError
            
            class Child(Base):
                pass
            """.trimIndent()
        )
        myFixture.enableInspections(PyAbstractMethodNotImplementedInChildInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testQuickFixImplementsMethod() {
        myFixture.configureByText(
            "a.py",
            """
            import abc
            
            class <warning descr="Child class 'Child' is missing implementation for abstract methods: foo"><caret>Base</warning>(abc.ABC):
                @abc.abstractmethod
                def foo(self):
                    raise NotImplementedError
            
            class Child(Base):
                pass
            """.trimIndent()
        )
        myFixture.enableInspections(PyAbstractMethodNotImplementedInChildInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fix = myFixture.findSingleIntention("Implement missing abstract methods in child classes")
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
            import abc

            class Base(abc.ABC):
                @abc.abstractmethod
                def foo(self):
                    raise NotImplementedError
            
            class Child(Base):
                def foo(self):
                    pass
            
                
            """.trimIndent()
        )
    }

    fun testAbstractChildIgnored() {
        myFixture.configureByText(
            "a.py",
            """
            import abc
            
            class <warning descr="Child class 'Concrete' is missing implementation for abstract methods: foo">Base</warning>(abc.ABC):
                @abc.abstractmethod
                def foo(self):
                    pass
            
            class <warning descr="Child class 'Concrete' is missing implementation for abstract methods: bar">Middle</warning>(Base):
                @abc.abstractmethod
                def bar(self):
                    pass
            
            class Concrete(Middle):
                pass
            """.trimIndent()
        )
        myFixture.enableInspections(PyAbstractMethodNotImplementedInChildInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }
}
