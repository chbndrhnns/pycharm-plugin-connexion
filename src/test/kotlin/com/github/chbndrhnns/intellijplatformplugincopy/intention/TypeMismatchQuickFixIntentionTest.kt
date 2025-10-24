package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.MyPlatformTestCase
import com.jetbrains.python.inspections.PyTypeCheckerInspection

internal class TypeMismatchQuickFixIntentionTest : MyPlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
    }

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

        val intention = myFixture.findSingleIntention("Wrap with str()")
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

    fun testWrapPathInStr() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: str = Path(<caret>"val")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from pathlib import Path
            a: str = str(Path("val"))
            """.trimIndent()
        )
    }

    fun testWrapStrInPath() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: Path = "<caret>val"
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with Path()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from pathlib import Path
            a: Path = Path("val")
            """.trimIndent()
        )
    }

    fun testWrapReturnValue() {
        myFixture.configureByText(
            "a.py", """
            def do(val: float) -> str:
                return <caret>val
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with str()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            def do(val: float) -> str:
                return str(val)
            """.trimIndent()
        )
    }

    fun testWrapParenthesizedStrInPath() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: Path = ("<caret>val")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with Path()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from pathlib import Path
            a: Path = Path("val")
            """.trimIndent()
        )
    }


    fun testWrapNestedParenthesizedStrInPath() {
        myFixture.configureByText(
            "a.py",
            """
            from pathlib import Path
            a: Path = (("<caret>val"))
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with Path()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from pathlib import Path
            a: Path = Path("val")
            """.trimIndent()
        )
    }

    fun testWrapWithTypeFromSecondModule() {
        // Create a second module with a custom type
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
            from custom_types import CustomWrapper
            
            def process_data(data: CustomWrapper) -> str:
                return data.value
            
            # This should trigger a type mismatch
            result = process_data(<caret>"some_string")
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val intention = myFixture.findSingleIntention("Wrap with CustomWrapper()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from custom_types import CustomWrapper
            
            def process_data(data: CustomWrapper) -> str:
                return data.value
            
            # This should trigger a type mismatch
            result = process_data(CustomWrapper("some_string"))
            """.trimIndent()
        )
    }

    fun testWrapWithUnimportedTypeFromSecondModule() {
        // Create a second module with a custom type
        myFixture.addFileToProject(
            "utils.py",
            """
            class DataProcessor:
                def __init__(self, raw_data: str):
                    self.data = raw_data.upper()
                    
                def process(self) -> str:
                    return self.data
            """.trimIndent()
        )

        myFixture.configureByText(
            "worker.py",
            """
            from utils import DataProcessor
            
            def handle_input(processor: DataProcessor) -> None:
                print(processor.process())
            
            # Type mismatch: expected DataProcessor, got str
            handle_input("<caret>raw_input_data")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with DataProcessor()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from utils import DataProcessor
            
            def handle_input(processor: DataProcessor) -> None:
                print(processor.process())
            
            # Type mismatch: expected DataProcessor, got str
            handle_input(DataProcessor("raw_input_data"))
            """.trimIndent()
        )
    }
}
