package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring.parameterobject

import fixtures.TestBase
import fixtures.assertActionNotAvailable
import fixtures.doRefactoringTest

class PyIntroduceParameterObjectRefactoringTest : TestBase() {

    private val actionId = INTRODUCE_PARAMETER_OBJECT_ACTION_ID

    fun testSimpleIntroduceParameterObject() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                actionId
            )
        }
    }

    fun testMethodIntroduceParameterObject() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                actionId
            )
        }
    }

    fun testAvailableFromCallSite() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                actionId
            )
        }
    }

    fun testKeywordOnlyParamsCallSiteUsesKeywordArgument() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                actionId
            )
        }
    }

    fun testIntroduceParameterObjectCreatesValidClassNameForDigitLeadingFunction() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                actionId
            )
        }
    }

    fun testUnavailableInFunctionBody() {
        myFixture.assertActionNotAvailable(
            "a.py",
            """
            def create_user(first_name, last_name):
                print(<caret>first_name, last_name)
            """.trimIndent(),
            actionId
        )
    }

    fun testUnavailableForPytestTestFunction() {
        myFixture.assertActionNotAvailable(
            "test_a.py",
            """
            def test_create_<caret>user(first_name, last_name):
                print(first_name, last_name)
            """.trimIndent(),
            actionId
        )
    }

    fun testUnavailableForPytestFixture() {
        myFixture.assertActionNotAvailable(
            "a.py",
            """
            import pytest


            @pytest.fixture
            def create_<caret>user(first_name, last_name):
                print(first_name, last_name)
            """.trimIndent(),
            actionId
        )
    }

    fun testSingleParameter() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                actionId
            )
        }
    }

    fun testAvailableOnReturnTypeAnnotation() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                actionId
            )
        }
    }

    fun testAvailableOnParameterTypeAnnotation() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
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
                actionId
            )
        }
    }
}
