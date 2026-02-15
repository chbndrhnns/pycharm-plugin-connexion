package com.github.chbndrhnns.betterpy.features.intentions.movescope

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class PyMoveClassIntoClassTest : TestBase() {

    private val intentionName = "BetterPy: Move to inner scope"

    // D3: Top-level class → nested class, refs updated
    fun testClassMovedIntoTargetClass() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    pass

                class In<caret>ner:
                    pass
            """,
            after = """
                class Outer:
                    class Inner:
                        pass
            """,
            intentionName = intentionName
        )
    }

    // D3: References updated ClassName → Outer.ClassName
    fun testClassReferencesUpdated() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    pass

                class In<caret>ner:
                    pass

                x = Inner()
            """,
            after = """
                class Outer:
                    class Inner:
                        pass

                x = Outer.Inner()
            """,
            intentionName = intentionName
        )
    }

    // D3: Class with methods preserved
    fun testClassWithMethodsPreserved() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    pass

                class In<caret>ner:
                    def method(self):
                        return 42
            """,
            after = """
                class Outer:
                    class Inner:
                        def method(self):
                            return 42
            """,
            intentionName = intentionName
        )
    }

    // D3: Target class with existing members — nested class appended
    fun testClassAppendedToTargetWithExistingMembers() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    def existing(self):
                        pass

                class In<caret>ner:
                    pass
            """,
            after = """
                class Outer:
                    def existing(self):
                        pass

                    class Inner:
                        pass
            """,
            intentionName = intentionName
        )
    }

    // D3: Decorated class moves with decorators
    fun testDecoratedClassMovesWithDecorators() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                def my_decorator(cls):
                    return cls

                class Outer:
                    pass

                @my_decorator
                class In<caret>ner:
                    pass
            """,
            after = """
                def my_decorator(cls):
                    return cls

                class Outer:
                    @my_decorator
                    class Inner:
                        pass
            """,
            intentionName = intentionName
        )
    }

    // D5: Name collision — intention blocked when target class already has nested class with same name
    fun testNotAvailableWhenNameCollision() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class Outer:
                    class Inner:
                        pass

                class In<caret>ner:
                    pass
            """,
            intentionName = intentionName
        )
    }

    // Not available when only one class in file (no target)
    fun testNotAvailableWhenOnlyOneClass() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class On<caret>ly:
                    pass
            """,
            intentionName = intentionName
        )
    }

    // Not available when more than two top-level classes (ambiguous target)
    fun testNotAvailableWhenMultipleTargetClasses() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class ClassA:
                    pass

                class ClassB:
                    pass

                class Class<caret>C:
                    pass
            """,
            intentionName = intentionName
        )
    }

    // Not available when caret is not on class name
    fun testNotAvailableOnClassBody() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class Outer:
                    pass

                class Inner:
                    pa<caret>ss
            """,
            intentionName = intentionName
        )
    }

    // Not available for nested class (already inside another class)
    fun testNotAvailableForNestedClass() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class Outer:
                    class In<caret>ner:
                        pass
            """,
            intentionName = intentionName
        )
    }
}
