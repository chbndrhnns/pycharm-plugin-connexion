package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.doIntentionTest

class PyIntroduceParameterObjectVariadicTest : TestBase() {

    fun testArgs() {
        myFixture.doIntentionTest(
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
            
            
            @dataclass
            class FooParams:
                a: Any
                b: Any
            
            
            def foo(params: FooParams, *args):
                print(params.a, params.b, args)
            
            
            def main():
                foo(FooParams(a=1, b=2), 3, 4)
            """.trimIndent() + "\n\n",
            "Introduce parameter object"
        )
    }
    
    fun testKwargs() {
        myFixture.doIntentionTest(
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
            
            
            @dataclass
            class FooParams:
                a: Any
                b: Any
            
            
            def foo(params: FooParams, **kwargs):
                print(params.a, params.b, kwargs)
            
            
            def main():
                foo(FooParams(a=1, b=2), x=3)
            """.trimIndent() + "\n\n",
            "Introduce parameter object"
        )
    }

    fun testKwOnlySeparator() {
        myFixture.doIntentionTest(
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
            
            
            @dataclass
            class FooParams:
                a: Any
                b: Any
            
            
            def foo(params: FooParams):
                print(params.a, params.b)
            
            
            def main():
                foo(FooParams(a=1, b=2))
            """.trimIndent() + "\n\n",
            "Introduce parameter object"
        )
    }

     fun testPositionalOnlySeparator() {
        myFixture.doIntentionTest(
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
            
            
             @dataclass
             class FooParams:
                 a: Any
                 b: Any
            
            
             def foo(params: FooParams, /):
                 print(params.a, params.b)
            
            
             def main():
                 foo(FooParams(a=1, b=2))
             """.trimIndent() + "\n\n",
            "Introduce parameter object"
        )
    }
}
