package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.UiInterceptors
import fixtures.TestBase
import fixtures.doIntentionTest

class PyIntroduceParameterObjectNameCollisionTest : TestBase() {

    fun testCollisionWithLocalClass() {
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
                class CreateUserParams:
                    pass
                
                def create_<caret>user(first_name, last_name):
                    print(first_name, last_name)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                class CreateUserParams:
                    pass
    
                
                @dataclass(frozen=True, slots=True)
                class CreateUserParams1:
                    first_name: Any
                    last_name: Any
                
                
                def create_user(params: CreateUserParams1):
                    print(params.first_name, params.last_name)
                """.trimIndent() + "\n\n",
                "Introduce parameter object"
            )
        } finally {
            UiInterceptors.clear()
        }
    }

    fun testCollisionWithImportedClass() {
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
                from other import CreateUserParams
                
                def create_<caret>user(first_name, last_name):
                    print(first_name, last_name)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                from other import CreateUserParams
                
                
                @dataclass(frozen=True, slots=True)
                class CreateUserParams1:
                    first_name: Any
                    last_name: Any
                
                
                def create_user(params: CreateUserParams1):
                    print(params.first_name, params.last_name)
                """.trimIndent() + "\n\n",
                "Introduce parameter object"
            )
        } finally {
            UiInterceptors.clear()
        }
    }
}
