package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import fixtures.TestBase
import fixtures.assertActionNotAvailable
import fixtures.doRefactoringTest

class PyIntroduceParameterObjectCasesTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.IntroduceParameterObjectRefactoringAction"

    fun testAsyncFunction() {
        val expected = """
            import asyncio
            from dataclasses import dataclass
            from typing import Any
            
            
            @dataclass(frozen=True, slots=True, kw_only=True)
            class FetchDataParams:
                url: Any
                timeout: Any = 10
                retries: Any = 3
            
            
            async def fetch_data(params: FetchDataParams):
                await asyncio.sleep(1)
            
            
            async def main():
                await fetch_data(FetchDataParams(url="http://example.com"))
        """.trimIndent() + "\n"

        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                import asyncio
                
                async def fetch_<caret>data(url, timeout=10, retries=3):
                    await asyncio.sleep(1)
                
                async def main():
                    await fetch_data("http://example.com")
                """.trimIndent(),
                expected,
                actionId
            )
        }
    }

    fun testProperty() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                class A:
                    @property
                    def prop(self):
                        return 1
                    
                    @prop.setter
                    def pro<caret>p(self, value, extra):
                        pass
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
    
    
                @dataclass(frozen=True, slots=True, kw_only=True)
                class PropParams:
                    value: Any
                    extra: Any
    
    
                class A:
                    @property
                    def prop(self):
                        return 1
    
                    @prop.setter
                    def prop(self, params: PropParams):
                        pass
                """.trimIndent(),
                actionId
            )
        }
    }

    fun testOverload() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                from typing import overload
                
                @overload
                def pro<caret>cess(a: int, b: int): ...
                
                def process(a, b):
                    pass
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import overload, Any
    
    
                @dataclass(frozen=True, slots=True, kw_only=True)
                class ProcessParams:
                    a: int
                    b: int
    
    
                @overload
                def process(params: ProcessParams): ...
    
    
                def process(a, b):
                    pass
                """.trimIndent(),
                actionId
            )
        }
    }

    fun testStubFile() {
        myFixture.assertActionNotAvailable(
            "a.pyi",
            """
            def fo<caret>o(a: int, b: int): ...
            """.trimIndent(),
            actionId
        )
    }

    fun testNestedFunction() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                def outer():
                    def in<caret>ner(a, b):
                        print(a, b)
                    
                    inner(1, 2)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
    
    
                def outer():
                    @dataclass(frozen=True, slots=True, kw_only=True)
                    class InnerParams:
                        a: Any
                        b: Any
    
                    def inner(params: InnerParams):
                        print(params.a, params.b)
    
                    inner(InnerParams(a=1, b=2))
                """.trimIndent(),
                actionId
            )
        }
    }
}
