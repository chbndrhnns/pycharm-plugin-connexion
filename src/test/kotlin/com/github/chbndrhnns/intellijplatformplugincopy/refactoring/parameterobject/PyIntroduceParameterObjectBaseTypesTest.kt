package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

/**
 * Tests for different base types in parameter object generation.
 */
class PyIntroduceParameterObjectBaseTypesTest : TestBase() {

    fun testDataclassBaseType() {
        myFixture.configureByText(
            "dataclass_test.py",
            """
            def my_func(name: str, age: int):
                print(name, age)
                
            my_func("John", 30)
            """
        )

        val function = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java).first()

        val processor = PyIntroduceParameterObjectProcessor(function) { allParams, defaultName ->
            IntroduceParameterObjectSettings(
                selectedParameters = allParams,
                className = defaultName,
                parameterName = "params",
                baseType = ParameterObjectBaseType.DATACLASS,
                generateFrozen = true,
                generateSlots = true,
                generateKwOnly = true
            )
        }

        processor.run()

        myFixture.checkResult(
            """
            from dataclasses import dataclass
            from typing import Any
            
            
            @dataclass(frozen=True, slots=True, kw_only=True)
            class MyFuncParams:
                name: str
                age: int
            
            
            def my_func(params: MyFuncParams):
                print(params.name, params.age)
            
            
            my_func(MyFuncParams(name="John", age=30))
            """.trimIndent() + "\n"
        )
    }

    fun testNamedTupleBaseType() {
        myFixture.configureByText(
            "namedtuple_test.py",
            """
            def my_func(name: str, age: int):
                print(name, age)
                
            my_func("John", 30)
            """
        )

        val function = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java).first()

        val processor = PyIntroduceParameterObjectProcessor(function) { allParams, defaultName ->
            IntroduceParameterObjectSettings(
                selectedParameters = allParams,
                className = defaultName,
                parameterName = "params",
                baseType = ParameterObjectBaseType.NAMED_TUPLE,
                generateFrozen = false,
                generateSlots = false,
                generateKwOnly = false
            )
        }

        processor.run()

        myFixture.checkResult(
            """
            from typing import NamedTuple, Any
            
            
            class MyFuncParams(NamedTuple):
                name: str
                age: int
            
            
            def my_func(params: MyFuncParams):
                print(params.name, params.age)
            
            
            my_func(MyFuncParams(name="John", age=30))
            """.trimIndent() + "\n"
        )
    }

    fun testTypedDictBaseType() {
        myFixture.configureByText(
            "typeddict_test.py",
            """
            def my_func(name: str, age: int):
                print(name, age)
                
            my_func("John", 30)
            """
        )

        val function = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java).first()

        val processor = PyIntroduceParameterObjectProcessor(function) { allParams, defaultName ->
            IntroduceParameterObjectSettings(
                selectedParameters = allParams,
                className = defaultName,
                parameterName = "params",
                baseType = ParameterObjectBaseType.TYPED_DICT,
                generateFrozen = false,
                generateSlots = false,
                generateKwOnly = false
            )
        }

        processor.run()

        myFixture.checkResult(
            """
            from typing import TypedDict, Any
            
            
            class MyFuncParams(TypedDict):
                name: str
                age: int
            
            
            def my_func(params: MyFuncParams):
                print(params["name"], params["age"])
            
            
            my_func(MyFuncParams(name="John", age=30))
            """.trimIndent() + "\n"
        )
    }

    fun testPydanticBaseModelType() {
        myFixture.configureByText(
            "pydantic_test.py",
            """
            def my_func(name: str, age: int):
                print(name, age)
                
            my_func("John", 30)
            """
        )

        val function = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java).first()

        val processor = PyIntroduceParameterObjectProcessor(function) { allParams, defaultName ->
            IntroduceParameterObjectSettings(
                selectedParameters = allParams,
                className = defaultName,
                parameterName = "params",
                baseType = ParameterObjectBaseType.PYDANTIC_BASE_MODEL,
                generateFrozen = false,
                generateSlots = false,
                generateKwOnly = false
            )
        }

        processor.run()

        myFixture.checkResult(
            """
            from typing import Any
            
            from pydantic import BaseModel
            
            
            class MyFuncParams(BaseModel):
                name: str
                age: int
            
            
            def my_func(params: MyFuncParams):
                print(params.name, params.age)
            
            
            my_func(MyFuncParams(name="John", age=30))
            """.trimIndent() + "\n"
        )
    }

    fun testPydanticWithFrozen() {
        myFixture.configureByText(
            "pydantic_frozen_test.py",
            """
            def my_func(name: str):
                print(name)
                
            my_func("John")
            """
        )

        val function = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java).first()

        val processor = PyIntroduceParameterObjectProcessor(function) { allParams, defaultName ->
            IntroduceParameterObjectSettings(
                selectedParameters = allParams,
                className = defaultName,
                parameterName = "params",
                baseType = ParameterObjectBaseType.PYDANTIC_BASE_MODEL,
                generateFrozen = true,
                generateSlots = false,
                generateKwOnly = false
            )
        }

        processor.run()

        myFixture.checkResult(
            """
            from typing import Any
            
            from pydantic import BaseModel
            
            
            class MyFuncParams(BaseModel):
                name: str
                model_config = {"frozen": True}
            
            
            def my_func(params: MyFuncParams):
                print(params.name)
            
            
            my_func(MyFuncParams(name="John"))
            """.trimIndent() + "\n"
        )
    }

    fun testNamedTupleWithDefaultValues() {
        myFixture.configureByText(
            "namedtuple_defaults_test.py",
            """
            def my_func(name: str, age: int = 25):
                print(name, age)
                
            my_func("John")
            """
        )

        val function = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java).first()

        val processor = PyIntroduceParameterObjectProcessor(function) { allParams, defaultName ->
            IntroduceParameterObjectSettings(
                selectedParameters = allParams,
                className = defaultName,
                parameterName = "params",
                baseType = ParameterObjectBaseType.NAMED_TUPLE,
                generateFrozen = false,
                generateSlots = false,
                generateKwOnly = false
            )
        }

        processor.run()

        myFixture.checkResult(
            """
            from typing import NamedTuple, Any
            
            
            class MyFuncParams(NamedTuple):
                name: str
                age: int = 25
            
            
            def my_func(params: MyFuncParams):
                print(params.name, params.age)
            
            
            my_func(MyFuncParams(name="John"))
            """.trimIndent() + "\n"
        )
    }
}
