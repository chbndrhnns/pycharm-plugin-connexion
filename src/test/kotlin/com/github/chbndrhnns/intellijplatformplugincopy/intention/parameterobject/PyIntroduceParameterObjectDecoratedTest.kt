package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.assertActionNotAvailable
import fixtures.doRefactoringTest

class PyIntroduceParameterObjectDecoratedTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.IntroduceParameterObjectRefactoringAction"

    fun testIntroduceClassMethod() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                class MyClass:
                    @classmethod
                    def my_method(cls, <caret>a, b):
                        print(cls, a, b)
                
                def main():
                    MyClass.my_method(1, 2)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class MyMethodParams:
                    a: Any
                    b: Any
                
                
                class MyClass:
                    @classmethod
                    def my_method(cls, params: MyMethodParams):
                        print(cls, params.a, params.b)
                
                
                def main():
                    MyClass.my_method(MyMethodParams(a=1, b=2))
                """.trimIndent(),
                actionId
            )
        }
    }

    fun testIntroduceStaticMethod() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                class MyClass:
                    @staticmethod
                    def my_method(<caret>a, b):
                        print(a, b)
                
                def main():
                    MyClass.my_method(1, 2)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class MyMethodParams:
                    a: Any
                    b: Any
                
                
                class MyClass:
                    @staticmethod
                    def my_method(params: MyMethodParams):
                        print(params.a, params.b)
                
                
                def main():
                    MyClass.my_method(MyMethodParams(a=1, b=2))
                """.trimIndent(),
                actionId
            )
        }
    }

    fun testIntroducePropertyDecorator() {
        // Standard property with only self - should not be available
        myFixture.assertActionNotAvailable(
            "a.py",
            """
            class MyClass:
                @property
                def my_prop(se<caret>lf):
                    return 1
            """.trimIndent(),
            actionId
        )
    }

    fun testIntroduceCustomDecorator() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                def my_decorator(f):
                    return f

                @my_decorator
                def my_func(<caret>a, b):
                    print(a, b)
                
                def main():
                    my_func(1, 2)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any


                def my_decorator(f):
                    return f
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class MyFuncParams:
                    a: Any
                    b: Any
                
                
                @my_decorator
                def my_func(params: MyFuncParams):
                    print(params.a, params.b)
                
                
                def main():
                    my_func(MyFuncParams(a=1, b=2))
                """.trimIndent(),
                actionId
            )
        }
    }
}
