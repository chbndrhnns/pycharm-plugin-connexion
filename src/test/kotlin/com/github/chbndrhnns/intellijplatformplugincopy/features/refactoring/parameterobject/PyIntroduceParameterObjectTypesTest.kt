package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyIntroduceParameterObjectTypesTest : TestBase() {

    fun testUnionType() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                INTRODUCE_PARAMETER_OBJECT_ACTION_ID
            )
        }
    }

    fun testAnnotatedType() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                INTRODUCE_PARAMETER_OBJECT_ACTION_ID
            )
        }
    }

    fun testForwardReferenceString() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                INTRODUCE_PARAMETER_OBJECT_ACTION_ID
            )
        }
    }

    fun testForwardReferenceFutureAnnotations() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                from __future__ import annotations
                
                def pro<caret>cess(user: User, count: int):
                    print(user, count)
                    
                class User: ...
                """.trimIndent(),
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
                """.trimIndent(),
                INTRODUCE_PARAMETER_OBJECT_ACTION_ID
            )
        }
    }
}
