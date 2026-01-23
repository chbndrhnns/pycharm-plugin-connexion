package com.github.chbndrhnns.betterpy.features.intentions.abstractmethod

import fixtures.TestBase

class PyMakeMemberAbstractInAbstractClassIntention : TestBase() {

    fun testOfferedOnMethodInAbcBaseClass() {
        myFixture.configureByText(
            "a.py",
            """
            import abc

            class Base(abc.ABC):
                def <caret>foo(self):
                    pass
            """.trimIndent()
        )

        val fix = myFixture.findSingleIntention("BetterPy: Make member abstract")
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
                def <caret>foo(self):
                    pass
            """.trimIndent()
        )

        val fix = myFixture.findSingleIntention("BetterPy: Make member abstract")
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
                def <caret>name(self) -> str:
                    return "x"
            """.trimIndent()
        )

        val fix = myFixture.findSingleIntention("BetterPy: Make member abstract")
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
                def <caret>foo(self):
                    pass
            """.trimIndent()
        )

        val intentions = myFixture.availableIntentions.map { it.text }
        if (intentions.contains("BetterPy: Make member abstract")) {
            throw AssertionError("Unexpected intention 'BetterPy: Make member abstract' available. Available: $intentions")
        }
    }

    fun testFixAddsImportAbcIfMissing() {
        myFixture.configureByText(
            "a.py",
            """
            from abc import ABC

            class Base(ABC):
                def <caret>foo(self):
                    pass
            """.trimIndent()
        )

        val fix = myFixture.findSingleIntention("BetterPy: Make member abstract")
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
