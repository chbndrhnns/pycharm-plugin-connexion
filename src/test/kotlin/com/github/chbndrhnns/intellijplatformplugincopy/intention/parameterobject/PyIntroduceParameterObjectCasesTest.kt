package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase

class PyIntroduceParameterObjectCasesTest : TestBase() {

    fun testAsyncFunction() {
        val before = """
            import asyncio
            
            async def fetch_<caret>data(url, timeout=10, retries=3):
                await asyncio.sleep(1)
            
            async def main():
                await fetch_data("http://example.com")
        """.trimIndent()

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

        myFixture.configureByText("a.py", before)
        val intention = myFixture.findSingleIntention("Introduce parameter object")

        withMockIntroduceParameterObjectDialog {
            myFixture.launchAction(intention)
        }
        myFixture.checkResult(expected)
    }

    fun testProperty() {
        val text = """
            class A:
                @property
                def prop(self):
                    return 1
                
                @prop.setter
                def pro<caret>p(self, value, extra):
                    pass
        """.trimIndent()

        myFixture.configureByText("a.py", text)
        val intention = myFixture.findSingleIntention("Introduce parameter object")

        withMockIntroduceParameterObjectDialog {
            myFixture.launchAction(intention)
        }
        myFixture.checkResult(
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

        """.trimIndent()
        )
    }

    fun testOverload() {
        val text = """
            from typing import overload
            
            @overload
            def pro<caret>cess(a: int, b: int): ...
            
            def process(a, b):
                pass
        """.trimIndent()

        myFixture.configureByText("a.py", text)
        val intention = myFixture.findSingleIntention("Introduce parameter object")

        withMockIntroduceParameterObjectDialog {
            myFixture.launchAction(intention)
        }
        myFixture.checkResult(
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

        """.trimIndent()
        )
    }

    fun testStubFile() {
        val text = """
            def fo<caret>o(a: int, b: int): ...
        """.trimIndent()

        myFixture.configureByText("a.pyi", text)
        assertEmpty(myFixture.filterAvailableIntentions("Introduce parameter object"))
    }

    fun testNestedFunction() {
        val before = """
            def outer():
                def in<caret>ner(a, b):
                    print(a, b)
                
                inner(1, 2)
        """.trimIndent()

        myFixture.configureByText("a.py", before)
        val intention = myFixture.findSingleIntention("Introduce parameter object")

        withMockIntroduceParameterObjectDialog {
            myFixture.launchAction(intention)
        }

        myFixture.checkResult(
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

        """.trimIndent()
        )
    }
}
