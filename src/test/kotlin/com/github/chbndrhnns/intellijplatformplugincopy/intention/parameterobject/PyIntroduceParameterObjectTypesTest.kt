package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.doIntentionTest

class PyIntroduceParameterObjectTypesTest : TestBase() {

    fun testUnionType() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                from typing import Union
                
                def pro<caret>cess(val: Union[int, str], count: int):
                    print(val, count)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Union, Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class ProcessParams:
                    val: Union[int, str]
                    count: int
                
                
                def process(params: ProcessParams):
                    print(params.val, params.count)
                """.trimIndent(),
                "Introduce parameter object"
            )
        }
    }

    fun testAnnotatedType() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                from typing import Annotated
                
                def pro<caret>cess(val: Annotated[int, "meta"], count: int):
                    print(val, count)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Annotated, Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class ProcessParams:
                    val: Annotated[int, "meta"]
                    count: int
                
                
                def process(params: ProcessParams):
                    print(params.val, params.count)
                """.trimIndent(),
                "Introduce parameter object"
            )
        }
    }

    fun testForwardReferenceString() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                def pro<caret>cess(user: "User", count: int):
                    print(user, count)
                    
                class User: ...
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class ProcessParams:
                    user: "User"
                    count: int
                
                
                def process(params: ProcessParams):
                    print(params.user, params.count)
                
                
                class User: ...
                """.trimIndent(),
                "Introduce parameter object"
            )
        }
    }

    fun testForwardReferenceFutureAnnotations() {
        val before = """
            from __future__ import annotations
            
            def pro<caret>cess(user: User, count: int):
                print(user, count)
                
            class User: ...
            """.trimIndent()

        myFixture.configureByText("a.py", before)
        val intention = myFixture.findSingleIntention("Introduce parameter object")

        withMockIntroduceParameterObjectDialog {
            myFixture.launchAction(intention)
        }

        myFixture.checkResult(
            """
            from __future__ import annotations

            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class ProcessParams:
                user: User
                count: int


            def process(params: ProcessParams):
                print(params.user, params.count)


            class User: ...
            
        """.trimIndent()
        )
    }
}
