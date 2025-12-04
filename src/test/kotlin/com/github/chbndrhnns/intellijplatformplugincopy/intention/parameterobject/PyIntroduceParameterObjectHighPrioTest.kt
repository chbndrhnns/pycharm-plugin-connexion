package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.UiInterceptors
import fixtures.TestBase
import fixtures.doIntentionTest

class PyIntroduceParameterObjectHighPrioTest : TestBase() {

    fun testDefaultValues() {
        val before = """
            def create_<caret>user(first_name, last_name="Doe", email="unknown"):
                print(first_name, last_name, email)
            
            def main():
                create_user("John")
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
        
        val after = myFixture.file.text
        
        val expected = """
            from dataclasses import dataclass
            from typing import Any
            
            
            @dataclass(frozen=True, slots=True)
            class CreateUserParams:
                first_name: Any
                last_name: Any = "Doe"
                email: Any = "unknown"
            
            
            def create_user(params: CreateUserParams):
                print(params.first_name, params.last_name, params.email)
            
            
            def main():
                create_user(CreateUserParams(first_name="John"))""".trimIndent() + "\n"
        
        assertEquals(expected, after)
    }

    fun testKeywordArgumentsAtCallSite() {
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
                def create_<caret>user(first_name, last_name):
                    print(first_name, last_name)
                
                def main():
                    create_user(first_name="John", last_name="Doe")
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True)
                class CreateUserParams:
                    first_name: Any
                    last_name: Any
                
                
                def create_user(params: CreateUserParams):
                    print(params.first_name, params.last_name)
                
                
                def main():
                    create_user(CreateUserParams(first_name="John", last_name="Doe"))""".trimIndent() + "\n",
                "Introduce parameter object"
            )
        } finally {
            UiInterceptors.clear()
        }
    }

    fun testClassMethod() {
        val before = """
            class UserFactory:
                @classmethod
                def create_<caret>user(cls, name, age):
                    print(cls, name, age)
            
            def main():
                UserFactory.create_user("John", 30)
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
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True)
            class CreateUserParams:
                name: Any
                age: Any


            class UserFactory:
                @classmethod
                def create_user(cls, params: CreateUserParams):
                    print(cls, params.name, params.age)


            def main():
                UserFactory.create_user(CreateUserParams(name="John", age=30))

        """.trimIndent()
        )
    }
    
    fun testStaticMethod() {
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
                class Utils:
                    @staticmethod
                    def hel<caret>per(x, y):
                        print(x, y)
                
                def main():
                    Utils.helper(1, 2)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any


                @dataclass(frozen=True, slots=True)
                class HelperParams:
                    x: Any
                    y: Any
                
                
                class Utils:
                    @staticmethod
                    def helper(params: HelperParams):
                        print(params.x, params.y)
                
                
                def main():
                    Utils.helper(HelperParams(x=1, y=2))""".trimIndent() + "\n",
                "Introduce parameter object"
            )
        } finally {
            UiInterceptors.clear()
        }
    }
}
