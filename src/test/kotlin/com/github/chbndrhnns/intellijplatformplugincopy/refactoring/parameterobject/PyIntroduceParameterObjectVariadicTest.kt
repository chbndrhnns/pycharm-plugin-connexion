package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyIntroduceParameterObjectVariadicTest : TestBase() {

    fun testArgs() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                def foo(a, <caret>b, *args):
                    print(a, b, args)
                    
                def main():
                    foo(1, 2, 3, 4)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class FooParams:
                    a: Any
                    b: Any
                
                
                def foo(params: FooParams, *args):
                    print(params.a, params.b, args)
                
                
                def main():
                    foo(FooParams(a=1, b=2), 3, 4)
                """.trimIndent(),
                "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.IntroduceParameterObjectRefactoringAction"
            )
        }
    }

    fun testKwargs() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                def foo(a, <caret>b, **kwargs):
                    print(a, b, kwargs)
                    
                def main():
                    foo(1, 2, x=3)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class FooParams:
                    a: Any
                    b: Any
                
                
                def foo(params: FooParams, **kwargs):
                    print(params.a, params.b, kwargs)
                
                
                def main():
                    foo(FooParams(a=1, b=2), x=3)
                """.trimIndent(),
                "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.IntroduceParameterObjectRefactoringAction"
            )
        }
    }

    fun testKwOnlySeparator() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                def foo(a, *, <caret>b):
                    print(a, b)
                    
                def main():
                    foo(1, b=2)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class FooParams:
                    a: Any
                    b: Any
                
                
                def foo(params: FooParams):
                    print(params.a, params.b)
                
                
                def main():
                    foo(FooParams(a=1, b=2))
                """.trimIndent(),
                "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.IntroduceParameterObjectRefactoringAction"
            )
        }
    }

    fun testPositionalOnlySeparator() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                def foo(a, <caret>b, /):
                    print(a, b)
                    
                def main():
                    foo(1, 2)
                """.trimIndent(),
                """
                 from dataclasses import dataclass
                 from typing import Any
                
                
                 @dataclass(frozen=True, slots=True, kw_only=True)
                 class FooParams:
                     a: Any
                     b: Any
                
                
                 def foo(params: FooParams, /):
                     print(params.a, params.b)
                
                
                 def main():
                     foo(FooParams(a=1, b=2))
                 """.trimIndent(),
                "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.IntroduceParameterObjectRefactoringAction"
            )
        }
    }
}
