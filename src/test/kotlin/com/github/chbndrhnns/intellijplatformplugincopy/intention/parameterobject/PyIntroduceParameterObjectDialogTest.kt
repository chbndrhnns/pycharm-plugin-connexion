package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PyIntroduceParameterObjectDialogTest : TestBase() {

    fun testPartialSelection() {
        myFixture.configureByText(
            "a.py",
            """
            def my_func(a, b, c):
                print(a, b, c)
                
            my_func(1, 2, 3)
            """
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
            ?: PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java).first()

        // Select only 'b' and 'c'
        val processor = PyIntroduceParameterObjectProcessor(function) { allParams, defaultName ->
            IntroduceParameterObjectSettings(
                allParams.filter { it.name in listOf("b", "c") },
                defaultName,
                "params"
            )
        }

        processor.run()

        myFixture.checkResult(
            """
            from dataclasses import dataclass
            from typing import Any
            
            
            @dataclass(frozen=True, slots=True, kw_only=True)
            class MyFuncParams:
                b: Any
                c: Any
            
            
            def my_func(a, params: MyFuncParams):
                print(a, params.b, params.c)
            
            
            my_func(1, MyFuncParams(b=2, c=3))
            """.trimIndent() + "\n"
        )
    }

    fun testSelectionWithGap() {
        // Test extracting 'a' and 'c', leaving 'b'
        myFixture.configureByText(
            "b.py",
            """
            def my_func(a, b, c):
                print(a, b, c)
                
            my_func(1, 2, 3)
            """
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
            ?: PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java).first()

        // Select 'a' and 'c'
        val processor = PyIntroduceParameterObjectProcessor(function) { allParams, defaultName ->
            IntroduceParameterObjectSettings(
                allParams.filter { it.name in listOf("a", "c") },
                defaultName,
                "params"
            )
        }

        processor.run()

        myFixture.checkResult(
            """
            from dataclasses import dataclass
            from typing import Any
            
            
            @dataclass(frozen=True, slots=True, kw_only=True)
            class MyFuncParams:
                a: Any
                c: Any
            
            
            def my_func(params: MyFuncParams, b):
                print(params.a, b, params.c)
            
            
            my_func(MyFuncParams(a=1, c=3), 2)
            """.trimIndent() + "\n"
        )
    }

    fun testCustomNamesAndOptions() {
        myFixture.configureByText(
            "c.py",
            """
            def my_func(a, b):
                print(a, b)
                
            my_func(1, 2)
            """
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
            ?: PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java).first()

        val processor = PyIntroduceParameterObjectProcessor(function) { allParams, _ ->
            IntroduceParameterObjectSettings(
                selectedParameters = allParams,
                className = "MyCustomParams",
                parameterName = "my_params",
                generateFrozen = false,
                generateSlots = false,
                generateKwOnly = false
            )
        }

        processor.run()

        myFixture.checkResult(
            """
            from dataclasses import dataclass
            from typing import Any
            
            
            @dataclass
            class MyCustomParams:
                a: Any
                b: Any
            
            
            def my_func(my_params: MyCustomParams):
                print(my_params.a, my_params.b)
            
            
            my_func(MyCustomParams(a=1, b=2))
            """.trimIndent() + "\n"
        )
    }

    fun testFrozenAndSlots() {
        myFixture.configureByText(
            "d.py",
            """
            def my_func(a):
                pass
            """
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
            ?: PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java).first()

        val processor = PyIntroduceParameterObjectProcessor(function) { allParams, _ ->
            IntroduceParameterObjectSettings(
                selectedParameters = allParams,
                className = "FrozenParams",
                parameterName = "args",
                generateFrozen = true,
                generateSlots = true
            )
        }

        processor.run()

        myFixture.checkResult(
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class FrozenParams:
                a: Any


            def my_func(args: FrozenParams):
                pass
            """.trimIndent() + "\n"
        )
    }
}
