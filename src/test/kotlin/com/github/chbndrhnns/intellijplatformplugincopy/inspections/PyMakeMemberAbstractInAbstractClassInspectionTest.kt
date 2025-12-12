package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import fixtures.TestBase

class PyMakeMemberAbstractInAbstractClassInspectionTest : TestBase() {

    fun testOfferedOnMethodInAbcBaseClass() {
        myFixture.configureByText(
            "a.py",
            """
            import abc

            class Base(abc.ABC):
                def <caret><warning descr="Member in abstract class should be abstract">foo</warning>(self):
                    pass
            """.trimIndent()
        )

        myFixture.enableInspections(PyMakeMemberAbstractInAbstractClassInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fix = myFixture.findSingleIntention("Make member abstract")
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
            import abc

            class Base(abc.ABC):
                @abc.abstractmethod
                def foo(self):
                    pass
            """.trimIndent()
        )
    }

    fun testOfferedOnMethodInAbcMetaMetaclass() {
        myFixture.configureByText(
            "a.py",
            """
            import abc

            class Base(metaclass=abc.ABCMeta):
                def <caret><warning descr="Member in abstract class should be abstract">foo</warning>(self):
                    pass
            """.trimIndent()
        )

        myFixture.enableInspections(PyMakeMemberAbstractInAbstractClassInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fix = myFixture.findSingleIntention("Make member abstract")
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
            import abc

            class Base(metaclass=abc.ABCMeta):
                @abc.abstractmethod
                def foo(self):
                    pass
            """.trimIndent()
        )
    }

    fun testOfferedOnPropertyKeepsDecoratorOrder() {
        myFixture.configureByText(
            "a.py",
            """
            import abc

            class Base(abc.ABC):
                @property
                def <caret><warning descr="Member in abstract class should be abstract">name</warning>(self) -> str:
                    return "x"
            """.trimIndent()
        )

        myFixture.enableInspections(PyMakeMemberAbstractInAbstractClassInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fix = myFixture.findSingleIntention("Make member abstract")
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
            import abc

            class Base(abc.ABC):
                @property
                @abc.abstractmethod
                def name(self) -> str:
                    return "x"
            """.trimIndent()
        )
    }

    fun testNotOfferedWhenAlreadyAbstract() {
        myFixture.configureByText(
            "a.py",
            """
            import abc

            class Base(abc.ABC):
                @abc.abstractmethod
                def foo(self):
                    pass
            """.trimIndent()
        )

        myFixture.enableInspections(PyMakeMemberAbstractInAbstractClassInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fixes = myFixture.getAllQuickFixes().map { it.familyName }
        if (fixes.contains("Make member abstract")) {
            throw AssertionError("Unexpected quick-fix 'Make member abstract' available. Available: $fixes")
        }
    }

    fun testFixAddsImportAbcIfMissing() {
        myFixture.configureByText(
            "a.py",
            """
            from abc import ABC

            class Base(ABC):
                def <caret><warning descr="Member in abstract class should be abstract">foo</warning>(self):
                    pass
            """.trimIndent()
        )

        myFixture.enableInspections(PyMakeMemberAbstractInAbstractClassInspection::class.java)
        myFixture.checkHighlighting(true, false, false)

        val fix = myFixture.findSingleIntention("Make member abstract")
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
                from abc import ABC, abstractmethod
                
                
                class Base(ABC):
                    @abstractmethod
                    def foo(self):
                        pass
            """.trimIndent()
        )
    }
}
