package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase
import fixtures.assertIntentionAvailable
import fixtures.assertIntentionNotAvailable
import fixtures.withPopulatePopupSelection

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
            "Populate arguments..."
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
            "Populate arguments..."
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
            "Populate arguments..."
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
            val intention = myFixture.findSingleIntention("Populate arguments...")
            myFixture.launchAction(intention)

            assertEquals("Populate arguments", fake.lastTitle)
            assertEquals(
                listOf("All arguments", "Required arguments only"),
                fake.lastLabels
            )
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
            val intention = myFixture.findSingleIntention("Populate arguments...")
            myFixture.launchAction(intention)

            assertEquals("Populate arguments", fake.lastTitle)
            assertEquals(
                listOf(
                    "All arguments",
                    "Required arguments only",
                    "All arguments (recursive)",
                    "Required arguments only (recursive)"
                ),
                fake.lastLabels
            )
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
            val intention = myFixture.findSingleIntention("Populate arguments...")
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
            val intention = myFixture.findSingleIntention("Populate arguments...")
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
            val intention = myFixture.findSingleIntention("Populate arguments...")
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
            val intention = myFixture.findSingleIntention("Populate arguments...")
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
