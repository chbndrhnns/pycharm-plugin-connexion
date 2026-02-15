package com.github.chbndrhnns.betterpy.features.intentions.movescope

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class PyMoveFunctionIntoClassTest : TestBase() {

    private val intentionName = "BetterPy: Move to inner scope"

    // D1: Top-level function → method (first param typed as class → becomes self)
    fun testFunctionWithTypedParamBecomesMethod() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class MyClass:
                    pass

                def proc<caret>ess(obj: MyClass, value: int) -> int:
                    return value + 1
            """,
            after = """
                class MyClass:
                    def process(self, value: int) -> int:
                        return value + 1
            """,
            intentionName = intentionName
        )
    }

    // D1 variant: call sites updated func(obj, args) → obj.func(args)
    fun testFunctionWithTypedParamCallSitesUpdated() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class MyClass:
                    pass

                def proc<caret>ess(obj: MyClass, value: int) -> int:
                    return value + 1

                x = MyClass()
                result = process(x, 42)
            """,
            after = """
                class MyClass:
                    def process(self, value: int) -> int:
                        return value + 1

                x = MyClass()
                result = x.process(42)
            """,
            intentionName = intentionName
        )
    }

    // D2: Top-level function → static method (no class-typed param)
    fun testFunctionWithoutClassParamBecomesStaticMethod() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class MyClass:
                    pass

                def hel<caret>per(value: int) -> int:
                    return value + 1
            """,
            after = """
                class MyClass:
                    @staticmethod
                    def helper(value: int) -> int:
                        return value + 1
            """,
            intentionName = intentionName
        )
    }

    // D2 variant: static method call sites updated func(args) → ClassName.func(args)
    fun testStaticMethodCallSitesUpdated() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class MyClass:
                    pass

                def hel<caret>per(value: int) -> int:
                    return value + 1

                result = helper(42)
            """,
            after = """
                class MyClass:
                    @staticmethod
                    def helper(value: int) -> int:
                        return value + 1

                result = MyClass.helper(42)
            """,
            intentionName = intentionName
        )
    }

    // D5: Name collision — intention blocked when target class already has method with same name
    fun testNotAvailableWhenNameCollision() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class MyClass:
                    def process(self):
                        pass

                def proc<caret>ess(obj: MyClass) -> None:
                    pass
            """,
            intentionName = intentionName
        )
    }

    // Not available when no classes in file
    fun testNotAvailableWhenNoClassesInFile() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                def hel<caret>per():
                    pass
            """,
            intentionName = intentionName
        )
    }

    // Not available for methods (already inside a class)
    fun testNotAvailableForMethod() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class MyClass:
                    def meth<caret>od(self):
                        pass
            """,
            intentionName = intentionName
        )
    }

    // Not available when caret is not on function name
    fun testNotAvailableOnFunctionBody() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class MyClass:
                    pass

                def helper():
                    pa<caret>ss
            """,
            intentionName = intentionName
        )
    }

    // Function with multiple classes — first param type determines target
    fun testFunctionMovesToCorrectClassByType() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class ClassA:
                    pass

                class ClassB:
                    pass

                def proc<caret>ess(obj: ClassB, value: int) -> int:
                    return value + 1
            """,
            after = """
                class ClassA:
                    pass

                class ClassB:
                    def process(self, value: int) -> int:
                        return value + 1
            """,
            intentionName = intentionName
        )
    }

    // Not available when multiple classes and no typed first param (ambiguous target)
    fun testNotAvailableWhenMultipleClassesAndNoTypedParam() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class ClassA:
                    pass

                class ClassB:
                    pass

                def hel<caret>per(value: int) -> int:
                    return value + 1
            """,
            intentionName = intentionName
        )
    }

    // D1: Function with self-only param (just the class-typed param, no others)
    fun testFunctionWithOnlyClassParam() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class MyClass:
                    pass

                def get_n<caret>ame(obj: MyClass) -> str:
                    return "name"
            """,
            after = """
                class MyClass:
                    def get_name(self) -> str:
                        return "name"
            """,
            intentionName = intentionName
        )
    }

    // Class with existing methods — new method appended
    fun testFunctionAppendedToClassWithExistingMethods() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class MyClass:
                    def existing(self):
                        pass

                def hel<caret>per(obj: MyClass, x: int) -> int:
                    return x
            """,
            after = """
                class MyClass:
                    def existing(self):
                        pass

                    def helper(self, x: int) -> int:
                        return x
            """,
            intentionName = intentionName
        )
    }
}
