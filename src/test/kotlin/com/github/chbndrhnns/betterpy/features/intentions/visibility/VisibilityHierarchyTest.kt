package com.github.chbndrhnns.betterpy.features.intentions.visibility

import fixtures.TestBase
import fixtures.doIntentionTest

class VisibilityHierarchyTest : TestBase() {

    fun testMakePrivate_OverridingMethod_RenamesInBaseClass() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class Base:
                def me<caret>thod(self):
                    pass
            
            class Derived(Base):
                def method(self):
                    pass
            """,
            """
            class Base:
                def _method(self):
                    pass
            
            class Derived(Base):
                def _method(self):
                    pass
            """,
            "BetterPy: Change visibility: make private"
        )
    }

    fun testMakePrivate_OverriddenMethod_RenamesInDerivedClass() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class Base:
                def method(self):
                    pass
            
            class Derived(Base):
                def me<caret>thod(self):
                    pass
            """,
            """
            class Base:
                def _method(self):
                    pass
            
            class Derived(Base):
                def _method(self):
                    pass
            """,
            "BetterPy: Change visibility: make private"
        )
    }

    fun testMakePrivate_MultipleInheritance_RenamesAllBases() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class Base1:
                def me<caret>thod(self):
                    pass
            
            class Base2:
                def method(self):
                    pass
            
            class Derived(Base1, Base2):
                def method(self):
                    pass
            """,
            """
            class Base1:
                def _method(self):
                    pass
            
            class Base2:
                def _method(self):
                    pass
            
            class Derived(Base1, Base2):
                def _method(self):
                    pass
            """,
            "BetterPy: Change visibility: make private"
        )
    }
}
