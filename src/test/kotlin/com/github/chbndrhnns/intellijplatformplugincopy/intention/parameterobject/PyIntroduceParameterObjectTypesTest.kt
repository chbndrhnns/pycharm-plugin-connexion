package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.UiInterceptors
import fixtures.TestBase
import fixtures.doIntentionTest

class PyIntroduceParameterObjectTypesTest : TestBase() {

    fun testUnionType() {
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
                from typing import Union
                
                def pro<caret>cess(val: Union[int, str], count: int):
                    print(val, count)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Union, Any
                
                
                @dataclass(frozen=True, slots=True)
                class ProcessParams:
                    val: Union[int, str]
                    count: int
                
                
                def process(params: ProcessParams):
                    print(params.val, params.count)
                """.trimIndent() + "\n",
                "Introduce parameter object"
            )
        } finally {
            UiInterceptors.clear()
        }
    }

    fun testAnnotatedType() {
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
                from typing import Annotated
                
                def pro<caret>cess(val: Annotated[int, "meta"], count: int):
                    print(val, count)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Annotated, Any
                
                
                @dataclass(frozen=True, slots=True)
                class ProcessParams:
                    val: Annotated[int, "meta"]
                    count: int
                
                
                def process(params: ProcessParams):
                    print(params.val, params.count)
                """.trimIndent() + "\n",
                "Introduce parameter object"
            )
        } finally {
            UiInterceptors.clear()
        }
    }

    fun testForwardReferenceString() {
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
                def pro<caret>cess(user: "User", count: int):
                    print(user, count)
                    
                class User: ...
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True)
                class ProcessParams:
                    user: "User"
                    count: int
                
                
                def process(params: ProcessParams):
                    print(params.user, params.count)
                
                
                class User: ...
                """.trimIndent() + "\n",
                "Introduce parameter object"
            )
        } finally {
            UiInterceptors.clear()
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

        UiInterceptors.register(object :
            UiInterceptors.UiInterceptor<IntroduceParameterObjectDialog>(IntroduceParameterObjectDialog::class.java) {
            override fun doIntercept(component: IntroduceParameterObjectDialog) {
                component.close(DialogWrapper.OK_EXIT_CODE)
            }
        })
        try {
            myFixture.launchAction(intention)
        } finally {
            UiInterceptors.clear()
        }

        myFixture.checkResult(
            """
            from __future__ import annotations

            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True)
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
