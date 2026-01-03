package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertRefactoringActionAvailable
import fixtures.doRefactoringActionTest

class ContainerTypesTest : TestBase() {

    private val actionId = "com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.IntroduceCustomTypeRefactoringAction"

    fun testList_IntentionAvailable() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            def f(x: li<caret>st[int]):
                pass
            """,
            actionId
        )
    }

    fun testSet_IntentionAvailable() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            def f(x: s<caret>et[int]):
                pass
            """,
            actionId
        )
    }

    fun testDict_IntentionAvailable() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            def f(x: di<caret>ct[str, int]):
                pass
            """,
            actionId
        )
    }

    fun testDict_GeneratesGenericCustomTypeAndKeepsArguments() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def do(arg: di<caret>ct[str, list[int]]) -> None:
                ...
            """,
            """
            class Customdict(dict[str, list[int]]):
                pass


            def do(arg: Customdict) -> None:
                ...
            """,
            actionId,
            renameTo = "Customdict"
        )
    }

    fun testList_GeneratesCustomType() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def f(x: li<caret>st[int]):
                pass
            """,
            """
                class Customlist(list[int]):
                    pass
                
                
                def f(x: Customlist):
                    pass
            """,
            actionId,
            renameTo = "Customlist"
        )
    }

    fun testDict_AnnotatedAssignment_WrapsValueAndKeepsArguments() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def do():
                val: di<caret>ct[str, int] = {
                    "a": 1,
                    "b": 2,
                    "c": 3,
                }
            """,
            """
            class Customdict(dict[str, int]):
                pass
            
            
            def do():
                val: Customdict = Customdict({
                    "a": 1,
                    "b": 2,
                    "c": 3,
                })
            """,
            actionId,
            renameTo = "Customdict"

        )
    }
}
