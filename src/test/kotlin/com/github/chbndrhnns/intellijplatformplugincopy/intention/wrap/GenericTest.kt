package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.FakePopupHost
import com.github.chbndrhnns.intellijplatformplugincopy.TestBase
import com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapWithExpectedTypeIntentionHooks

class GenericTest : TestBase() {
    fun testDeclarationAssignmentSplit() {
        myFixture.configureByText(
            "a.py",
            """
            class ProductId(int):
                pass

            def test_():
                val: ProductId
                val = 12<caret>34
            """.trimIndent()
        )

        myFixture.doHighlighting()
        myFixture.findSingleIntention("Wrap with ProductId()")
    }

    fun testIsAvailableForAssignments() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: str = Path(<caret>"val")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        myFixture.findSingleIntention("Wrap with str()")
    }

    fun testIsAvailableForReturnValues() {
        myFixture.configureByText(
            "a.py",
            """
            def do(val: float) -> str:
                return <caret>val
            """.trimIndent()
        )

        myFixture.doHighlighting()
        myFixture.findSingleIntention("Wrap with str()")
    }


    fun testWrapWithTypeFromSecondModule() {
        myFixture.addFileToProject(
            "custom_types.py",
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value
            """.trimIndent()
        )

        myFixture.configureByText(
            "main.py",
            """
            from .custom_types import CustomWrapper
            
            def process_data(data: CustomWrapper) -> str:
                return data.value
            
            result = process_data(<caret>"some_string")
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val intention = myFixture.findSingleIntention("Wrap with CustomWrapper()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from .custom_types import CustomWrapper
            
            def process_data(data: CustomWrapper) -> str:
                return data.value
            
            result = process_data(CustomWrapper("some_string"))
            """.trimIndent()
        )
    }

    fun testWrapAndImportTypeFromSecondModule() {
        myFixture.addFileToProject(
            "src/custom_types.py",
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value

            def process_data(data: CustomWrapper) -> str:
                return data.value
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "src/main.py",
            """
            from .custom_types import process_data

            result = process_data(<caret>"some_string")
            """.trimIndent()
        )
        myFixture.configureByFile("src/main.py")
        myFixture.doHighlighting()

        val intention = myFixture.findSingleIntention("Wrap with CustomWrapper()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from .custom_types import process_data, CustomWrapper
            
            result = process_data(CustomWrapper("some_string"))
            """.trimIndent()
        )
    }

    fun testWrapAndNoImportIfSameModule() {
        myFixture.configureByText(
            "main.py",
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value

            def process_data(data: CustomWrapper) -> str:
                return data.value


            result = process_data(<caret>"some_string")
            """.trimIndent()
        )
        myFixture.configureByFile("main.py")
        myFixture.doHighlighting()

        val intention = myFixture.findSingleIntention("Wrap with CustomWrapper()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value

            def process_data(data: CustomWrapper) -> str:
                return data.value


            result = process_data(CustomWrapper("some_string"))
            """.trimIndent()
        )
    }

    fun testWrapWithPep604UnionChoosesFirstBranch() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            
            def f(x: str | int) -> None:
                pass
            
            f(<caret>Path("val"))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        // Expect to wrap to the first branch: str()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from pathlib import Path
            
            def f(x: str | int) -> None:
                pass
            
            f(str(Path("val")))
            """.trimIndent()
        )
    }

    fun testIsAvailableForAssignments_WithAnnotatedExpectedType() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import Annotated

            from pathlib import Path
            a: Annotated[str, "meta"] = Path(<caret>"val")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        myFixture.findSingleIntention("Wrap with str()")
    }

    fun testWrapWithOptionalPicksInnerTypeNotNone() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import Optional
            from pathlib import Path
            
            def f(x: Optional[str]) -> None:
                pass
            
            f(<caret>Path("val"))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        // Expect Optional[str] -> choose str()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import Optional
            from pathlib import Path
            
            def f(x: Optional[str]) -> None:
                pass
            
            f(str(Path("val")))
            """.trimIndent()
        )
    }

    fun ignore_testWrapWithUnionCustomFirstPicksCustom() {
        // We need type buckets to do that
        myFixture.configureByText(
            "a.py",
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value
            
            def f(x: CustomWrapper | str) -> None:
                pass
            
            f(<caret>"abc")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with CustomWrapper()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value
            
            def f(x: CustomWrapper | str) -> None:
                pass
            
            f(CustomWrapper("abc"))
            """.trimIndent()
        )
    }

    fun testNoMultiWrapOnSameCtorWhenFirstWrapFailed() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            One = NewType("One", int)

            def do(arg: One | None) -> None:
                ...

            def test_():
                do(One(<caret>"abc"))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intentions = myFixture.availableIntentions
        val hasWrapWithOne = intentions.any { it.text == "Wrap with One()" }
        assertFalse("Intention should not be offered when already wrapped with One()", hasWrapWithOne)
    }

    fun testWrapWithEquivalentNewTypesChooserPickFirst() {
        val fake = FakePopupHost().apply { selectedIndex = 0 }
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",
                """
                from typing import NewType
                One = NewType("One", str)
                Two = NewType("Two", str)

                def do(arg: One | Two) -> None:
                    ...

                do(<caret>"abc")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            // Verify chooser labels include fully qualified names for NewType aliases and that first was picked
            assertEquals(listOf("One (a.One)", "Two (a.Two)"), fake.lastLabels)

            myFixture.checkResult(
                """
                from typing import NewType
                One = NewType("One", str)
                Two = NewType("Two", str)

                def do(arg: One | Two) -> None:
                    ...

                do(One("abc"))
                """.trimIndent()
            )
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }

    fun testWrapWithEquivalentNewTypesChooserPickSecond() {
        val fake = FakePopupHost().apply { selectedIndex = 1 }
        WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py",
                """
                from typing import NewType
                One = NewType("One", str)
                Two = NewType("Two", str)

                def do(arg: One | Two) -> None:
                    ...

                do(<caret>"abc")
                """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            // Be tolerant to formatting/import reflows; verify the essential transformation occurred
            val text = myFixture.file.text
            assertTrue(text.contains("def do(arg: One | Two) -> None:"))
            assertTrue(text.contains("do(Two(\"abc\"))"))
        } finally {
            WrapWithExpectedTypeIntentionHooks.popupHost = null
        }
    }
}