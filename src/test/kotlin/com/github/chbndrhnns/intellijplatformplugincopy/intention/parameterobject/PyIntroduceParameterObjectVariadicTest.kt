package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.UiInterceptors
import fixtures.TestBase
import fixtures.doIntentionTest

class PyIntroduceParameterObjectVariadicTest : TestBase() {

    fun testArgs() {
        UiInterceptors.register(object :
            UiInterceptors.UiInterceptor<IntroduceParameterObjectDialog>(IntroduceParameterObjectDialog::class.java) {
            override fun doIntercept(component: IntroduceParameterObjectDialog) {
                component.close(DialogWrapper.OK_EXIT_CODE)
            }
        })
        try {
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
                
                
                @dataclass(frozen=True, slots=True)
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
        } finally {
            UiInterceptors.clear()
        }
    }
    
    fun testKwargs() {
        UiInterceptors.register(object :
            UiInterceptors.UiInterceptor<IntroduceParameterObjectDialog>(IntroduceParameterObjectDialog::class.java) {
            override fun doIntercept(component: IntroduceParameterObjectDialog) {
                component.close(DialogWrapper.OK_EXIT_CODE)
            }
        })
        try {
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
                
                
                @dataclass(frozen=True, slots=True)
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
        } finally {
            UiInterceptors.clear()
        }
    }

    fun testKwOnlySeparator() {
        UiInterceptors.register(object :
            UiInterceptors.UiInterceptor<IntroduceParameterObjectDialog>(IntroduceParameterObjectDialog::class.java) {
            override fun doIntercept(component: IntroduceParameterObjectDialog) {
                component.close(DialogWrapper.OK_EXIT_CODE)
            }
        })
        try {
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
                
                
                @dataclass(frozen=True, slots=True)
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
        } finally {
            UiInterceptors.clear()
        }
    }

     fun testPositionalOnlySeparator() {
        UiInterceptors.register(object :
            UiInterceptors.UiInterceptor<IntroduceParameterObjectDialog>(IntroduceParameterObjectDialog::class.java) {
            override fun doIntercept(component: IntroduceParameterObjectDialog) {
                component.close(DialogWrapper.OK_EXIT_CODE)
            }
        })
        try {
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
                
                
                 @dataclass(frozen=True, slots=True)
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
        } finally {
            UiInterceptors.clear()
        }
    }
}
