package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class CustomTypeLibraryTest : TestBase() {

    fun testIntentionNotAvailable_WhenArgumentToJsonDump() {
        myFixture.configureByText(
            "a.py",
            """
            import json
            
            def do(f):
                json.dump({}, f, indent=<caret>2)
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type")
        assertEmpty("Intention should not be available for library function arguments", intentions)
    }

    fun testIntentionNotAvailable_WhenArgumentToBuiltinPrint() {
        myFixture.configureByText(
            "a.py",
            """
            def do():
                print("hello", end=<caret>"\n")
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type")
        assertEmpty("Intention should not be available for builtin function arguments", intentions)
    }

    fun testIntentionAvailable_WhenArgumentToUserFunction() {
        myFixture.configureByText(
            "a.py",
            """
            def my_func(x: int):
                pass
            
            def do():
                my_func(x=<caret>2)
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type")
        assertNotEmpty(intentions)
    }

    fun testIntentionAvailable_WhenInDictPassedToLibrary() {
        // Nested expression should still work
        myFixture.configureByText(
            "a.py",
            """
            import json
            
            def do(f):
                json.dump({"key": <caret>123}, f)
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type")
        assertNotEmpty(intentions)
    }
}
