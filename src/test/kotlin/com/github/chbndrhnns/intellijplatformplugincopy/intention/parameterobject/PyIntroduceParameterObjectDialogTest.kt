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
        val processor = PyIntroduceParameterObjectProcessor(function) { allParams ->
            allParams.filter { it.name in listOf("b", "c") }
        }

        processor.run()

        myFixture.checkResult(
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass
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
        val processor = PyIntroduceParameterObjectProcessor(function) { allParams ->
            allParams.filter { it.name in listOf("a", "c") }
        }

        processor.run()

        myFixture.checkResult(
            """
            from dataclasses import dataclass
            from typing import Any
            
            
            @dataclass
            class MyFuncParams:
                a: Any
                c: Any
            
            
            def my_func(params: MyFuncParams, b):
                print(params.a, b, params.c)
            
            
            my_func(MyFuncParams(a=1, c=3), 2)
            """.trimIndent() + "\n"
        )
    }
}
