package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.TestBase
import fixtures.doIntentionTest

class PyIntroduceParameterObjectIntentionTest : TestBase() {

    fun testSimpleIntroduceParameterObject() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                def create_<caret>user(first_name, last_name, email, age):
                    print(first_name, last_name, email, age)
                
                
                def main():
                    create_user("John", "Doe", "john@example.com", 30)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class CreateUserParams:
                    first_name: Any
                    last_name: Any
                    email: Any
                    age: Any
                
                
                def create_user(params: CreateUserParams):
                    print(params.first_name, params.last_name, params.email, params.age)
                
                
                def main():
                    create_user(CreateUserParams(first_name="John", last_name="Doe", email="john@example.com", age=30))
                """.trimIndent(),
                "Introduce parameter object"
            )
        }
    }

    fun testMethodIntroduceParameterObject() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                class Class:
                    def do(self, <caret>arg1, arg2):
                        return {}
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class DoParams:
                    arg1: Any
                    arg2: Any
                
                
                class Class:
                    def do(self, params: DoParams):
                        return {}""".trimIndent(),
                "Introduce parameter object"
            )
        }
    }

    fun testAvailableFromCallSite() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                def create_user(first_name, last_name):
                    print(first_name, last_name)


                def main():
                    create_<caret>user("John", "Doe")
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
                    create_user(CreateUserParams(first_name="John", last_name="Doe"))
                """.trimIndent(),
                "Introduce parameter object"
            )
        }
    }

    fun testKeywordOnlyParamsCallSiteUsesKeywordArgument() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                def do(*, arg1, arg2):
                    ...


                do(ar<caret>g1=1, arg2=2)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any


                @dataclass(frozen=True, slots=True, kw_only=True)
                class DoParams:
                    arg1: Any
                    arg2: Any


                def do(*, params: DoParams):
                    ...


                do(params=DoParams(arg1=1, arg2=2))
                """.trimIndent(),
                "Introduce parameter object"
            )
        }
    }

    fun testUnavailableInFunctionBody() {
        myFixture.configureByText(
            "a.py",
            """
            def create_user(first_name, last_name):
                print(<caret>first_name, last_name)
            """.trimIndent()
        )

        assertEmpty(myFixture.filterAvailableIntentions("Introduce parameter object"))
    }

    fun testUnavailableForPytestTestFunction() {
        myFixture.configureByText(
            "test_a.py",
            """
            def test_create_<caret>user(first_name, last_name):
                print(first_name, last_name)
            """.trimIndent()
        )

        assertEmpty(myFixture.filterAvailableIntentions("Introduce parameter object"))
    }

    fun testUnavailableForPytestFixture() {
        myFixture.configureByText(
            "a.py",
            """
            import pytest


            @pytest.fixture
            def create_<caret>user(first_name, last_name):
                print(first_name, last_name)
            """.trimIndent()
        )

        assertEmpty(myFixture.filterAvailableIntentions("Introduce parameter object"))
    }
}
