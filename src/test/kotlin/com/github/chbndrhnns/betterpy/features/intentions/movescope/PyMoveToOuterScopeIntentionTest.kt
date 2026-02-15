package com.github.chbndrhnns.betterpy.features.intentions.movescope

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class PyMoveToOuterScopeIntentionTest : TestBase() {

    private val intentionName = "BetterPy: Move to outer scope"

    // U1: Nested class → top-level, refs updated
    fun testNestedClassToTopLevel() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    class In<caret>ner:
                        pass

                    def use(self) -> "Outer.Inner":
                        return Outer.Inner()
            """,
            after = """
                class Outer:
                    def use(self) -> "Inner":
                        return Inner()

                class Inner:
                    pass
            """,
            intentionName = intentionName
        )
    }

    // U1 variant: simple nested class with no references
    fun testNestedClassToTopLevelNoRefs() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    class In<caret>ner:
                        pass
            """,
            after = """
                class Outer:
                    pass

                class Inner:
                    pass
            """,
            intentionName = intentionName
        )
    }

    // E1: Decorated nested class — decorators move with it
    fun testDecoratedNestedClassMovesWithDecorators() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                def my_decorator(cls):
                    return cls

                class Outer:
                    @my_decorator
                    class In<caret>ner:
                        pass
            """,
            after = """
                def my_decorator(cls):
                    return cls

                class Outer:
                    pass

                @my_decorator
                class Inner:
                    pass
            """,
            intentionName = intentionName
        )
    }

    // Not available for top-level class
    fun testNotAvailableForTopLevelClass() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class Top<caret>Level:
                    pass
            """,
            intentionName = intentionName
        )
    }

    // Caret on method inside nested class — now available as "move method to top-level" (Phase 3)
    fun testAvailableOnMethodInsideNestedClass() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    class Inner:
                        def meth<caret>od(self):
                            pass
            """,
            after = """
                class Outer:
                    class Inner:
                        pass

                def method(self: Inner):
                    pass
            """,
            intentionName = intentionName
        )
    }

    // Nested class with body containing methods
    fun testNestedClassWithMethods() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    class In<caret>ner:
                        def method(self):
                            return 42
            """,
            after = """
                class Outer:
                    pass

                class Inner:
                    def method(self):
                        return 42
            """,
            intentionName = intentionName
        )
    }

    // Nested class with sibling methods — outer class keeps other members
    fun testNestedClassWithSiblingMembers() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    def existing_method(self):
                        pass

                    class In<caret>ner:
                        pass
            """,
            after = """
                class Outer:
                    def existing_method(self):
                        pass

                class Inner:
                    pass
            """,
            intentionName = intentionName
        )
    }

    // U8: Name collision at target scope — intention should not be available
    fun testNotAvailableWhenNameCollisionAtTopLevel() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class Inner:
                    pass

                class Outer:
                    class In<caret>ner:
                        pass
            """,
            intentionName = intentionName
        )
    }

    // E5: Nested class used as type annotation in outer class
    fun testNestedClassUsedAsTypeAnnotation() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    class In<caret>ner:
                        pass

                    def method(self, x: "Outer.Inner") -> "Outer.Inner":
                        obj: Outer.Inner = Outer.Inner()
                        return obj
            """,
            after = """
                class Outer:
                    def method(self, x: "Inner") -> "Inner":
                        obj: Inner = Inner()
                        return obj

                class Inner:
                    pass
            """,
            intentionName = intentionName
        )
    }

    // U7: Doubly-nested class → one level up (to outer class level, not top-level)
    fun testDoublyNestedClassMovesOneLevelUp() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class A:
                    class B:
                        class <caret>C:
                            pass
            """,
            after = """
                class A:
                    class B:
                        pass

                    class C:
                        pass
            """,
            intentionName = intentionName
        )
    }

    // E2: Nested class inherits from outer class
    fun testNestedClassInheritingFromOuter() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    class In<caret>ner(Outer):
                        pass
            """,
            after = """
                class Outer:
                    pass

                class Inner(Outer):
                    pass
            """,
            intentionName = intentionName
        )
    }

    // E11: Caret on whitespace — not available
    fun testNotAvailableOnWhitespaceBetweenClasses() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class Outer:
                    class Inner1:
                        pass
                    <caret>
                    class Inner2:
                        pass
            """,
            intentionName = intentionName
        )
    }

    // U2: Nested class references outer class members — intention blocked
    fun testNotAvailableWhenNestedClassReferencesOuterMembers() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class Outer:
                    class_var = 42

                    class In<caret>ner:
                        def method(self):
                            return Outer.class_var
            """,
            intentionName = intentionName
        )
    }

    // U2 variant: references outer method
    fun testNotAvailableWhenNestedClassReferencesOuterMethod() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class Outer:
                    @staticmethod
                    def helper():
                        pass

                    class In<caret>ner:
                        def method(self):
                            Outer.helper()
            """,
            intentionName = intentionName
        )
    }

    // U2 variant: referencing Outer itself (not Outer.something) is OK
    fun testAvailableWhenNestedClassReferencesOuterNameOnly() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    class In<caret>ner(Outer):
                        pass
            """,
            after = """
                class Outer:
                    pass

                class Inner(Outer):
                    pass
            """,
            intentionName = intentionName
        )
    }

    // E9: Nested class with __slots__ referencing outer class member
    fun testNotAvailableWhenSlotsReferenceOuter() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                class Outer:
                    some_attr = "value"

                    class In<caret>ner:
                        __slots__ = ('x',)
                        def method(self):
                            return Outer.some_attr
            """,
            intentionName = intentionName
        )
    }

    // E12: Moving the only member out of a class — inserts pass
    fun testMovingOnlyMemberInsertsPass() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    class In<caret>ner:
                        pass
            """,
            after = """
                class Outer:
                    pass

                class Inner:
                    pass
            """,
            intentionName = intentionName
        )
    }

    // Multiple nested classes — only the targeted one moves
    fun testMultipleNestedClassesOnlyTargetMoves() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                class Outer:
                    class In<caret>ner1:
                        pass

                    class Inner2:
                        pass
            """,
            after = """
                class Outer:
                    class Inner2:
                        pass

                class Inner1:
                    pass
            """,
            intentionName = intentionName
        )
    }
}
