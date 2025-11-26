package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase

class ContainerTypesTest : TestBase() {

    fun testList_IntentionAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: li<caret>st[int]):
                pass
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from list")
        assertNotEmpty(intentions)
    }

    fun testSet_IntentionAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: s<caret>et[int]):
                pass
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from set")
        assertNotEmpty(intentions)
    }

    fun testDict_IntentionAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: di<caret>ct[str, int]):
                pass
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from dict")
        assertNotEmpty(intentions)
    }

    fun testDict_GeneratesGenericCustomTypeAndKeepsArguments() {
        myFixture.configureByText(
            "a.py",
            """
            def do(arg: di<caret>ct[str, list[int]]) -> None:
                ...
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from dict")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customdict(dict[str, list[int]]):
                pass


            def do(arg: Customdict) -> None:
                ...
            """.trimIndent()
        )
    }

    fun testList_GeneratesCustomType() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: li<caret>st[int]):
                pass
            """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from list")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
                class Customlist(list[int]):
                    pass
                
                
                def f(x: Customlist):
                    pass
            """.trimIndent()
        )
    }

    fun testDict_AnnotatedAssignment_WrapsValueAndKeepsArguments() {
        myFixture.configureByText(
            "a.py",
            """
            def do():
                val: di<caret>ct[str, int] = {
                    "a": 1,
                    "b": 2,
                    "c": 3,
                }
            """.trimIndent(),
        )

        val intention = myFixture.findSingleIntention("Introduce custom type from dict")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customdict(dict[str, int]):
                pass
            
            
            def do():
                val: Customdict = Customdict({
                    "a": 1,
                    "b": 2,
                    "c": 3,
                })
                """.trimIndent(),
        )
    }
}
