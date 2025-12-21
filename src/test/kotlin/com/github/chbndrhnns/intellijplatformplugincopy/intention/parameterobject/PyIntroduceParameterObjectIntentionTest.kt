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
                "BetterPy: Introduce parameter object"
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
                "BetterPy: Introduce parameter object"
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
                "BetterPy: Introduce parameter object"
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
                "BetterPy: Introduce parameter object"
            )
        }
    }

    fun testIntroduceParameterObjectCreatesValidClassNameForDigitLeadingFunction() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                def _1(<caret>abc, defg):
                    print(abc, defg)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any


                @dataclass(frozen=True, slots=True, kw_only=True)
                class _1Params:
                    abc: Any
                    defg: Any


                def _1(params: _1Params):
                    print(params.abc, params.defg)
                """.trimIndent(),
                "BetterPy: Introduce parameter object"
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

        assertEmpty(myFixture.filterAvailableIntentions("BetterPy: Introduce parameter object"))
    }

    fun testUnavailableForPytestTestFunction() {
        myFixture.configureByText(
            "test_a.py",
            """
            def test_create_<caret>user(first_name, last_name):
                print(first_name, last_name)
            """.trimIndent()
        )

        assertEmpty(myFixture.filterAvailableIntentions("BetterPy: Introduce parameter object"))
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

        assertEmpty(myFixture.filterAvailableIntentions("BetterPy: Introduce parameter object"))
    }

    fun testSingleParameter() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                def process_<caret>data(data):
                    print(data)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class ProcessDataParams:
                    data: Any
                
                
                def process_data(params: ProcessDataParams):
                    print(params.data)
                """.trimIndent(),
                "BetterPy: Introduce parameter object"
            )
        }
    }

    fun testAvailableOnReturnTypeAnnotation() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                def create_user(first_name: str, last_name: str) -> No<caret>ne:
                    print(first_name, last_name)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class CreateUserParams:
                    first_name: str
                    last_name: str
                
                
                def create_user(params: CreateUserParams) -> None:
                    print(params.first_name, params.last_name)
                """.trimIndent(),
                "BetterPy: Introduce parameter object"
            )
        }
    }

    fun testAvailableOnParameterTypeAnnotation() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doIntentionTest(
                "a.py",
                """
                def create_user(first_name: st<caret>r, last_name: str) -> None:
                    print(first_name, last_name)
                """.trimIndent(),
                """
                from dataclasses import dataclass
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class CreateUserParams:
                    first_name: str
                    last_name: str
                
                
                def create_user(params: CreateUserParams) -> None:
                    print(params.first_name, params.last_name)
                """.trimIndent(),
                "BetterPy: Introduce parameter object"
            )
        }
    }
}
