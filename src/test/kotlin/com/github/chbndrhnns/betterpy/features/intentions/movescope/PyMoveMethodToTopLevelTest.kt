package com.github.chbndrhnns.betterpy.features.intentions.movescope

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class PyMoveMethodToTopLevelTest : TestBase() {

    private val intentionName = "BetterPy: Move to outer scope"

    // U6: Method → top-level function, self becomes first typed parameter
    fun testMethodToTopLevelFunction() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class MyClass:
                    def gre<caret>et(self, name):
                        return f"Hello, {name}"

                obj = MyClass()
                print(obj.greet("world"))
            """,
            after = """
                class MyClass:
                    pass

                obj = MyClass()
                print(greet(obj, "world"))

                def greet(self: MyClass, name):
                    return f"Hello, {name}"
            """,
            intentionName = intentionName
        )
    }

    // U6 variant: Method with multiple methods in class — class keeps other members
    fun testMethodToTopLevelKeepsSiblings() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class MyClass:
                    def other(self):
                        pass

                    def gre<caret>et(self, name):
                        return f"Hello, {name}"
            """,
            after = """
                class MyClass:
                    def other(self):
                        pass

                def greet(self: MyClass, name):
                    return f"Hello, {name}"
            """,
            intentionName = intentionName
        )
    }

    // U6 variant: Method with no call sites
    fun testMethodToTopLevelNoCallSites() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class MyClass:
                    def gre<caret>et(self, name):
                        return f"Hello, {name}"
            """,
            after = """
                class MyClass:
                    pass

                def greet(self: MyClass, name):
                    return f"Hello, {name}"
            """,
            intentionName = intentionName
        )
    }

    // U6 variant: Method with self-only parameter
    fun testMethodWithSelfOnly() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class MyClass:
                    def do_st<caret>uff(self):
                        return 42

                obj = MyClass()
                result = obj.do_stuff()
            """,
            after = """
                class MyClass:
                    pass

                obj = MyClass()
                result = do_stuff(obj)

                def do_stuff(self: MyClass):
                    return 42
            """,
            intentionName = intentionName
        )
    }

    // E7: Method with super() calls — intention should be blocked
    fun testNotAvailableWithSuperCall() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class Parent:
                    def greet(self):
                        return "hello"

                class Child(Parent):
                    def gre<caret>et(self):
                        return super().greet()
            """,
            intentionName = intentionName
        )
    }

    // E8: @property method — intention should be blocked
    fun testNotAvailableForPropertyMethod() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class MyClass:
                    @property
                    def na<caret>me(self):
                        return self._name
            """,
            intentionName = intentionName
        )
    }

    // E8 variant: @property setter — intention should be blocked
    fun testNotAvailableForPropertySetter() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class MyClass:
                    @name.setter
                    def na<caret>me(self, value):
                        self._name = value
            """,
            intentionName = intentionName
        )
    }

    // Not available for dunder methods
    fun testNotAvailableForDunderMethod() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class MyClass:
                    def __in<caret>it__(self):
                        pass
            """,
            intentionName = intentionName
        )
    }

    // Not available for @staticmethod
    fun testNotAvailableForStaticMethod() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class MyClass:
                    @staticmethod
                    def hel<caret>per():
                        pass
            """,
            intentionName = intentionName
        )
    }

    // Not available for @classmethod
    fun testNotAvailableForClassMethod() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class MyClass:
                    @classmethod
                    def cre<caret>ate(cls):
                        return cls()
            """,
            intentionName = intentionName
        )
    }

    // Name collision at module level — intention blocked
    fun testNotAvailableWhenNameCollisionAtModuleLevel() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                def greet():
                    pass

                class MyClass:
                    def gre<caret>et(self, name):
                        return name
            """,
            intentionName = intentionName
        )
    }

    // Method with body containing self references
    fun testMethodWithSelfReferences() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class MyClass:
                    def proc<caret>ess(self, data):
                        self.data = data
                        return self.data
            """,
            after = """
                class MyClass:
                    pass

                def process(self: MyClass, data):
                    self.data = data
                    return self.data
            """,
            intentionName = intentionName
        )
    }
}
