package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyIntroduceParameterObjectInheritanceTest : TestBase() {

    private val actionId = INTRODUCE_PARAMETER_OBJECT_ACTION_ID

    fun testIntroduceFromChildClass() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                class Parent:
                    def foo(self, a, b):
                        print(a, b)

                class Child(Parent):
                    def fo<caret>o(self, a, b):
                        print(a, b)

                def main():
                    p = Parent()
                    p.foo(1, 2)
                    c = Child()
                    c.foo(3, 4)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any


                @dataclass(frozen=True, slots=True, kw_only=True)
                class FooParams:
                    a: Any
                    b: Any


                class Parent:
                    def foo(self, params: FooParams):
                        print(params.a, params.b)


                class Child(Parent):
                    def foo(self, params: FooParams):
                        print(params.a, params.b)


                def main():
                    p = Parent()
                    p.foo(FooParams(a=1, b=2))
                    c = Child()
                    c.foo(FooParams(a=3, b=4))
                """.trimIndent(),
                actionId
            )
        }
    }

    fun testIntroduceFromParentClass() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                class Parent:
                    def fo<caret>o(self, a, b):
                        print(a, b)

                class Child(Parent):
                    def foo(self, a, b):
                        print(a, b)

                def main():
                    p = Parent()
                    p.foo(1, 2)
                    c = Child()
                    c.foo(3, 4)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any


                @dataclass(frozen=True, slots=True, kw_only=True)
                class FooParams:
                    a: Any
                    b: Any


                class Parent:
                    def foo(self, params: FooParams):
                        print(params.a, params.b)


                class Child(Parent):
                    def foo(self, params: FooParams):
                        print(params.a, params.b)


                def main():
                    p = Parent()
                    p.foo(FooParams(a=1, b=2))
                    c = Child()
                    c.foo(FooParams(a=3, b=4))
                """.trimIndent(),
                actionId
            )
        }
    }

    fun testIntroduceWithSuperCall() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                class Parent:
                    def foo(self, a, b):
                        print(a, b)

                class Child(Parent):
                    def fo<caret>o(self, a, b):
                        super().foo(a, b)
                        print(a, b)

                def main():
                    c = Child()
                    c.foo(1, 2)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any


                @dataclass(frozen=True, slots=True, kw_only=True)
                class FooParams:
                    a: Any
                    b: Any


                class Parent:
                    def foo(self, params: FooParams):
                        print(params.a, params.b)


                class Child(Parent):
                    def foo(self, params: FooParams):
                        super().foo(FooParams(a=params.a, b=params.b))
                        print(params.a, params.b)


                def main():
                    c = Child()
                    c.foo(FooParams(a=1, b=2))
                """.trimIndent(),
                actionId
            )
        }
    }

    fun testIntroduceAcrossMultiLevelHierarchy() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                class Base:
                    def foo(self, a, b):
                        print(a, b)

                class Mid(Base):
                    def foo(self, a, b):
                        print(a, b)

                class Child(Mid):
                    def fo<caret>o(self, a, b):
                        print(a, b)

                def main():
                    Base().foo(1, 2)
                    Mid().foo(3, 4)
                    Child().foo(5, 6)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any


                @dataclass(frozen=True, slots=True, kw_only=True)
                class FooParams:
                    a: Any
                    b: Any


                class Base:
                    def foo(self, params: FooParams):
                        print(params.a, params.b)


                class Mid(Base):
                    def foo(self, params: FooParams):
                        print(params.a, params.b)


                class Child(Mid):
                    def foo(self, params: FooParams):
                        print(params.a, params.b)


                def main():
                    Base().foo(FooParams(a=1, b=2))
                    Mid().foo(FooParams(a=3, b=4))
                    Child().foo(FooParams(a=5, b=6))
                """.trimIndent(),
                actionId
            )
        }
    }
}
