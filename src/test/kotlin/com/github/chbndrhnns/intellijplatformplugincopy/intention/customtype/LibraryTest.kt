package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertRefactoringActionAvailable
import fixtures.assertRefactoringActionNotAvailable

class LibraryTest : TestBase() {

    private val actionId = "com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.IntroduceCustomTypeRefactoringAction"

    fun testIntentionNotAvailable_WhenArgumentToJsonDump() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            import json
            
            def do(f):
                json.dump({}, f, indent=<caret>2)
            """,
            actionId
        )
    }

    fun testIntentionNotAvailable_WhenArgumentToBuiltinPrint() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            def do():
                print("hello", end=<caret>"\n")
            """,
            actionId
        )
    }

    fun testIntentionAvailable_WhenArgumentToUserFunction() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            def my_func(x: int):
                pass
            
            def do():
                my_func(x=<caret>2)
            """,
            actionId
        )
    }

    fun testIntentionAvailable_WhenInDictPassedToLibrary() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            import json
            
            def do(f):
                json.dump({"key": <caret>123}, f)
            """,
            actionId
        )
    }
}
