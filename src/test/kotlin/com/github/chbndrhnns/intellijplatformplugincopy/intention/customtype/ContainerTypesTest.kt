package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertIntentionAvailable
import fixtures.doIntentionTest

class ContainerTypesTest : TestBase() {

    fun testList_IntentionAvailable() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            def f(x: li<caret>st[int]):
                pass
            """,
            "Introduce custom type from list"
        )
    }

    fun testSet_IntentionAvailable() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            def f(x: s<caret>et[int]):
                pass
            """,
            "Introduce custom type from set"
        )
    }

    fun testDict_IntentionAvailable() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            def f(x: di<caret>ct[str, int]):
                pass
            """,
            "Introduce custom type from dict"
        )
    }

    fun testDict_GeneratesGenericCustomTypeAndKeepsArguments() {
        myFixture.doIntentionTest(
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
            "Introduce custom type from dict",
            renameTo = "Customdict"
        )
    }

    fun testList_GeneratesCustomType() {
        myFixture.doIntentionTest(
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
            "Introduce custom type from list",
            renameTo = "Customlist"
        )
    }

    fun testDict_AnnotatedAssignment_WrapsValueAndKeepsArguments() {
        myFixture.doIntentionTest(
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
            "Introduce custom type from dict",
            renameTo = "Customdict"

        )
    }
}
