package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertIntentionAvailable
import fixtures.assertIntentionNotAvailable

class LibraryTest : TestBase() {

    fun testIntentionNotAvailable_WhenArgumentToJsonDump() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            import json
            
            def do(f):
                json.dump({}, f, indent=<caret>2)
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionNotAvailable_WhenArgumentToBuiltinPrint() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def do():
                print("hello", end=<caret>"\n")
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionAvailable_WhenArgumentToUserFunction() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            def my_func(x: int):
                pass
            
            def do():
                my_func(x=<caret>2)
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionAvailable_WhenInDictPassedToLibrary() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            import json
            
            def do(f):
                json.dump({"key": <caret>123}, f)
            """,
            "Introduce custom type"
        )
    }
}
