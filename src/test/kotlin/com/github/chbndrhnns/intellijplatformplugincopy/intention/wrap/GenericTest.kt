package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

class GenericTest : TestBase() {

    fun testWrapIntentionPreviewShowsActualCode() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: str = Path(<caret>"val")
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val intention = myFixture.findSingleIntention("Wrap with str()")
        val previewText = myFixture.getIntentionPreviewText(intention)
        assertEquals("str(Path(\"val\"))", previewText)
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

    fun testWrapIntentionTextUsesActualType() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path  
            a: str = Path(<caret>"val")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intentions = myFixture.availableIntentions
        val wrapIntention = intentions.find { it.text.startsWith("Wrap with") }

        // Verify that the intention text shows the actual expected type
        assertNotNull("Wrap intention should be available", wrapIntention)
        assertEquals("Intention text should show actual expected type", "Wrap with str()", wrapIntention?.text)
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

    fun testWrapWithUnionCustomFirstPicksCustom() {
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
}