package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.*

class GenericTest : TestBase() {
    fun testDeclarationAssignmentSplit() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            class ProductId(int):
                pass

            def test_():
                val: ProductId
                val = 12<caret>34
            """,
            "BetterPy: Wrap with ProductId()"
        )
    }

    fun testIsAvailableForAssignments() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            from pathlib import Path
            a: str = Path(<caret>"val")
            """,
            "BetterPy: Wrap with str()"
        )
    }

    fun testIsAvailableForReturnValues() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            def do(val: float) -> str:
                return <caret>val
            """,
            "BetterPy: Wrap with str()"
        )
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

        myFixture.doIntentionTest(
            "main.py",
            """
            from .custom_types import CustomWrapper
            
            def process_data(data: CustomWrapper) -> str:
                return data.value
            
            result = process_data(<caret>"some_string")
            """,
            """
            from .custom_types import CustomWrapper
            
            def process_data(data: CustomWrapper) -> str:
                return data.value
            
            result = process_data(CustomWrapper("some_string"))
            """,
            "BetterPy: Wrap with CustomWrapper()"
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

        val intention = myFixture.findSingleIntention("BetterPy: Wrap with CustomWrapper()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from .custom_types import process_data, CustomWrapper
            
            result = process_data(CustomWrapper("some_string"))
            """.trimIndent()
        )
    }

    fun testWrapAndNoImportIfSameModule() {
        myFixture.doIntentionTest(
            "main.py",
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value

            def process_data(data: CustomWrapper) -> str:
                return data.value


            result = process_data(<caret>"some_string")
            """,
            """
            class CustomWrapper:
                def __init__(self, value: str):
                    self.value = value

            def process_data(data: CustomWrapper) -> str:
                return data.value


            result = process_data(CustomWrapper("some_string"))
            """,
            "BetterPy: Wrap with CustomWrapper()"
        )
    }

    fun testWrapWithPep604UnionChoosesFirstBranch() {
        withWrapPopupSelection(0) { fake ->
            myFixture.doIntentionTest(
                "a.py",
                """
                from pathlib import Path
                
                def f(x: str | int) -> None:
                    pass
                
                f(<caret>Path("val"))
                """,
                """
                from pathlib import Path
                
                def f(x: str | int) -> None:
                    pass
                
                f(str(Path("val")))
                """,
                "BetterPy: Wrap with expected union type…"
            )
            val labels = fake.lastLabels
            assertTrue("Expected 'str' in chooser, got: ${'$'}labels", labels.any { it.startsWith("str") })
            assertTrue("Expected 'int' in chooser, got: ${'$'}labels", labels.any { it.startsWith("int") })
        }
    }

    fun testIsAvailableForAssignments_WithAnnotatedExpectedType() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            from typing import Annotated

            from pathlib import Path
            a: Annotated[str, "meta"] = Path(<caret>"val")
            """,
            "BetterPy: Wrap with str()"
        )
    }

    fun testWrapWithOptionalPicksInnerTypeNotNone() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from typing import Optional
            from pathlib import Path
            
            def f(x: Optional[str]) -> None:
                pass
            
            f(<caret>Path("val"))
            """,
            """
            from typing import Optional
            from pathlib import Path
            
            def f(x: Optional[str]) -> None:
                pass
            
            f(str(Path("val")))
            """,
            "BetterPy: Wrap with str()"
        )
    }

    fun ignore_testWrapWithUnionCustomFirstPicksCustom() {
        // Ignored for now, that's a case for introducing a box.
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
        val intention = myFixture.findSingleIntention("BetterPy: Wrap with CustomWrapper()")
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

    fun testWrapWithUnionCustomFirstPicksCustom() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class CustomWrapper(str): ...
            
            
            def f(x: CustomWrapper | str) -> None:
                pass
            
            
            f(<caret>123)
            """,
            """
            class CustomWrapper(str): ...
            
            
            def f(x: CustomWrapper | str) -> None:
                pass
            
            
            f(CustomWrapper(123))
            """,
            "BetterPy: Wrap with CustomWrapper()"
        )
    }

    fun testNoMultiWrapOnSameCtorWhenFirstWrapFailed() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import NewType
            One = NewType("One", int)

            def do(arg: One | None) -> None:
                ...

            def test_():
                do(One(<caret>"abc"))
            """,
            "BetterPy: Wrap with One()"
        )
    }

    fun testWrapWithEquivalentNewTypesChooserPickFirst() {
        withWrapPopupSelection(0) { fake ->
            myFixture.doIntentionTest(
                "a.py",
                """
                from typing import NewType
                One = NewType("One", str)
                Two = NewType("Two", str)

                def do(arg: One | Two) -> None:
                    ...

                do(<caret>"abc")
                """,
                """
                from typing import NewType
                One = NewType("One", str)
                Two = NewType("Two", str)

                def do(arg: One | Two) -> None:
                    ...

                do(One("abc"))
                """,
                "BetterPy: Wrap with expected union type…"
            )
            // Verify chooser labels include fully qualified names for NewType aliases and that first was picked
            assertEquals(listOf("One (a.One)", "Two (a.Two)"), fake.lastLabels)
        }
    }

    fun testWrapWithEquivalentNewTypesChooserPickSecond() {
        withWrapPopupSelection(1) {
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
            val intention = myFixture.findSingleIntention("BetterPy: Wrap with expected union type…")
            myFixture.launchAction(intention)

            // Be tolerant to formatting/import reflows; verify the essential transformation occurred
            val text = myFixture.file.text
            assertTrue(text.contains("def do(arg: One | Two) -> None:"))
            assertTrue(text.contains("do(Two(\"abc\"))"))
        }
    }
}