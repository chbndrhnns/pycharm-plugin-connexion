package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyIntroduceParameterObjectHighPrioTest : TestBase() {

    fun testDefaultValues() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                def create_<caret>user(first_name, last_name="Doe", email="unknown"):
                    print(first_name, last_name, email)
                
                def main():
                    create_user("John")
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class CreateUserParams:
                    first_name: Any
                    last_name: Any = "Doe"
                    email: Any = "unknown"
                
                
                def create_user(params: CreateUserParams):
                    print(params.first_name, params.last_name, params.email)
                
                
                def main():
                    create_user(CreateUserParams(first_name="John"))
                """.trimIndent(),
                "com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.IntroduceParameterObjectRefactoringAction"
            )
        }
    }

    fun testKeywordArgumentsAtCallSite() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class CreateUserParams:
                    first_name: Any
                    last_name: Any
                
                
                def create_user(params: CreateUserParams):
                    print(params.first_name, params.last_name)
                
                
                def main():
                    create_user(CreateUserParams(first_name="John", last_name="Doe"))""".trimIndent(),
                "com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.IntroduceParameterObjectRefactoringAction"
            )
        }
    }

    fun testClassMethod() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                class UserFactory:
                    @classmethod
                    def create_<caret>user(cls, name, age):
                        print(cls, name, age)
                
                def main():
                    UserFactory.create_user("John", 30)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
    
    
                @dataclass(frozen=True, slots=True, kw_only=True)
                class CreateUserParams:
                    name: Any
                    age: Any
    
    
                class UserFactory:
                    @classmethod
                    def create_user(cls, params: CreateUserParams):
                        print(cls, params.name, params.age)
    
    
                def main():
                    UserFactory.create_user(CreateUserParams(name="John", age=30))
                """.trimIndent(),
                "com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.IntroduceParameterObjectRefactoringAction"
            )
        }
    }

    fun testStaticMethod() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class HelperParams:
                    x: Any
                    y: Any
                
                
                class Utils:
                    @staticmethod
                    def helper(params: HelperParams):
                        print(params.x, params.y)
                
                
                def main():
                    Utils.helper(HelperParams(x=1, y=2))""".trimIndent(),
                "com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.IntroduceParameterObjectRefactoringAction"
            )
        }
    }
}
