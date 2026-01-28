package com.github.chbndrhnns.betterpy.features.intentions.populate

import fixtures.*

class PopulateArgumentsIntentionTest : TestBase() {

    // ==================== Availability Tests ====================

    fun testAvailable_DataclassWithMissingArgs() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            from dataclasses import dataclass

            @dataclass
            class A:
                x: int
                y: int

            a = A(<caret>)
            """,
            "BetterPy: Populate arguments..."
        )
    }

    fun testNotAvailable_PositionalOnlyFunction() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def sleep(__secs, /):
                pass

            sleep(1<caret>)
            """,
            "BetterPy: Populate arguments..."
        )
    }

    fun testNotAvailable_NoMissingArgs() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from dataclasses import dataclass

            @dataclass
            class A:
                x: int

            a = A(x=1<caret>)
            """,
            "BetterPy: Populate arguments..."
        )
    }

    // ==================== Popup Options Tests ====================

    fun testPopup_ShowsNonRecursiveOptions_WhenNoNestedDataclass() {
        withPopulatePopupSelection(0) { fake ->
            myFixture.configureByText(
                "a.py",
                """
                from dataclasses import dataclass

                @dataclass
                class A:
                    x: int
                    y: str

                a = A(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("BetterPy: Populate arguments...")
            myFixture.launchAction(intention)

            assertEquals("Populate arguments", fake.lastTitle)
            assertEquals(
                listOf(
                    "All arguments",
                    "Required arguments only"
                ),
                fake.lastModeLabels
            )
            assertFalse(fake.lastRecursiveAvailable)
            assertTrue(fake.lastLocalsAvailable)
        }
    }

    fun testPopup_ShowsAllOptions_WhenNestedDataclassPresent() {
        withPopulatePopupSelection(0) { fake ->
            myFixture.configureByText(
                "a.py",
                """
                from dataclasses import dataclass

                @dataclass
                class Inner:
                    value: int

                @dataclass
                class Outer:
                    inner: Inner
                    name: str

                o = Outer(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("BetterPy: Populate arguments...")
            myFixture.launchAction(intention)

            assertEquals("Populate arguments", fake.lastTitle)
            assertEquals(
                listOf(
                    "All arguments",
                    "Required arguments only"
                ),
                fake.lastModeLabels
            )
            assertTrue(fake.lastRecursiveAvailable)
            assertTrue(fake.lastLocalsAvailable)
        }
    }

    // ==================== Mode: All Arguments ====================

    fun testPopulateAll_Dataclass() {
        withPopulatePopupSelection(0) { // "All arguments"
            myFixture.configureByText(
                "a.py",
                """
                from dataclasses import dataclass

                @dataclass
                class A:
                    x: int
                    y: int
                    z: int = 1

                a = A(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("BetterPy: Populate arguments...")
            myFixture.launchAction(intention)

            myFixture.checkResult(
                """
                from dataclasses import dataclass

                @dataclass
                class A:
                    x: int
                    y: int
                    z: int = 1

                a = A(x=..., y=..., z=...)
                
                """.trimIndent()
            )
        }
    }

    // ==================== Mode: Required Only ====================

    fun testPopulateRequired_Dataclass() {
        withPopulatePopupSelection(1) { // "Required arguments only"
            myFixture.configureByText(
                "a.py",
                """
                from dataclasses import dataclass

                @dataclass
                class A:
                    x: int
                    y: int
                    z: int = 1

                a = A(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("BetterPy: Populate arguments...")
            myFixture.launchAction(intention)

            myFixture.checkResult(
                """
                from dataclasses import dataclass

                @dataclass
                class A:
                    x: int
                    y: int
                    z: int = 1

                a = A(x=..., y=...)
                
                """.trimIndent()
            )
        }
    }

    fun testPopulate_UsesConstructorsForBuiltins() {
        val optionsHost = FakePopulateOptionsPopupHost(
            selectedOptions = PopulateOptions(
                mode = PopulateMode.ALL,
                recursive = false,
                useLocalScope = false,
                useConstructors = true
            )
        )
        PopulateArgumentsIntentionHooks.optionsPopupHost = optionsHost
        try {
            myFixture.configureByText(
                "a.py",
                """
                def foo(x: int, y: str):
                    pass

                foo(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("BetterPy: Populate arguments...")
            myFixture.launchAction(intention)

            myFixture.checkResult(
                """
                def foo(x: int, y: str):
                    pass

                foo(x=int(), y=str())
                
                """.trimIndent()
            )
        } finally {
            PopulateArgumentsIntentionHooks.optionsPopupHost = null
        }
    }

    fun testPopulate_UsesConstructorsForBuiltinSubclass() {
        val optionsHost = FakePopulateOptionsPopupHost(
            selectedOptions = PopulateOptions(
                mode = PopulateMode.ALL,
                recursive = false,
                useLocalScope = false,
                useConstructors = true
            )
        )
        PopulateArgumentsIntentionHooks.optionsPopupHost = optionsHost
        try {
            myFixture.configureByText(
                "a.py",
                """
                class MyInt(int):
                    pass

                def foo(x: MyInt):
                    pass

                foo(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("BetterPy: Populate arguments...")
            myFixture.launchAction(intention)

            myFixture.checkResult(
                """
                class MyInt(int):
                    pass

                def foo(x: MyInt):
                    pass

                foo(x=MyInt())
                
                """.trimIndent()
            )
        } finally {
            PopulateArgumentsIntentionHooks.optionsPopupHost = null
        }
    }

    fun testPopulate_ConstructorsSkipRequiredArgs() {
        val optionsHost = FakePopulateOptionsPopupHost(
            selectedOptions = PopulateOptions(
                mode = PopulateMode.ALL,
                recursive = false,
                useLocalScope = false,
                useConstructors = true
            )
        )
        PopulateArgumentsIntentionHooks.optionsPopupHost = optionsHost
        try {
            myFixture.configureByText(
                "a.py",
                """
                class NeedsArg:
                    def __init__(self, value):
                        self.value = value

                def foo(x: NeedsArg):
                    pass

                foo(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("BetterPy: Populate arguments...")
            myFixture.launchAction(intention)

            myFixture.checkResult(
                """
                class NeedsArg:
                    def __init__(self, value):
                        self.value = value

                def foo(x: NeedsArg):
                    pass

                foo(x=...)
                
                """.trimIndent()
            )
        } finally {
            PopulateArgumentsIntentionHooks.optionsPopupHost = null
        }
    }

    // ==================== Mode: Recursive ====================
    // Note: Detailed recursive behavior is tested in RecursiveArgumentsIntentionTest.
    // These tests verify the unified intention correctly delegates to recursive mode.

    // ==================== Edge Cases ====================

    fun testPartialArguments_OnlyMissingPopulated() {
        withPopulatePopupSelection(0) { // "All arguments"
            myFixture.configureByText(
                "a.py",
                """
                def foo(a, b, c=3):
                    pass

                foo(1, <caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("BetterPy: Populate arguments...")
            myFixture.launchAction(intention)

            myFixture.checkResult(
                """
                def foo(a, b, c=3):
                    pass

                foo(1, b=..., c=...)
                
                """.trimIndent()
            )
        }
    }

    fun testKwOnlyMethod() {
        withPopulatePopupSelection(1) { // "Required arguments only"
            myFixture.configureByText(
                "a.py",
                """
                class C:
                    def foo(self, *, a, b=1):
                        pass

                C().foo(<caret>)
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("BetterPy: Populate arguments...")
            myFixture.launchAction(intention)

            myFixture.checkResult(
                """
                class C:
                    def foo(self, *, a, b=1):
                        pass

                C().foo(a=...)
                
                """.trimIndent()
            )
        }
    }
}
