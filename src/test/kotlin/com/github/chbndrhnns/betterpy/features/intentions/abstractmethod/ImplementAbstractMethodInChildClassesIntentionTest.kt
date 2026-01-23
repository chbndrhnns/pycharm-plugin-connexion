package com.github.chbndrhnns.betterpy.features.intentions.abstractmethod

import fixtures.TestBase

class ImplementAbstractMethodInChildClassesIntentionTest : TestBase() {

    fun testImplementAbstractMethod() {
        myFixture.configureByText(
            "a.py",
            """
            import abc
            
            class Base(abc.ABC):
                @abc.abstractmethod
                def ab<caret>stract_method(self, arg: int) -> str:
                    raise NotImplementedError
            
            class Child(Base):
                pass
            """
        )
        val action = myFixture.findSingleIntention("BetterPy: Implement abstract method in child classes")
        myFixture.launchAction(action)

        myFixture.checkResult(
            """
            import abc
            
            class Base(abc.ABC):
                @abc.abstractmethod
                def abstract_method(self, arg: int) -> str:
                    raise NotImplementedError
            
            class Child(Base):
                def abstract_method(self, arg: int) -> str:
                    pass
            """
        )
    }
}
