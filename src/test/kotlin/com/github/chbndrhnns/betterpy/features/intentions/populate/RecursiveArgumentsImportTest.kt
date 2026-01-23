package com.github.chbndrhnns.betterpy.features.intentions.populate

import fixtures.TestBase
import fixtures.doIntentionTest
import fixtures.withPopulatePopupSelection

class RecursiveArgumentsImportTest : TestBase() {

    fun testMissingImportAddedFromOneOtherFile() {
        myFixture.addFileToProject(
            "models.py",
            """
            from dataclasses import dataclass

            @dataclass
            class Other:
                val: int
            
            @dataclass
            class Main:
                f: Other
            """.trimIndent()
        )

        withPopulatePopupSelection(index = 2) {
            myFixture.doIntentionTest(
                "main.py",
                """
            from dataclasses import dataclass
            
            from .models import Main


            def test():
                m = Main(<caret>)
            """,
                """
            from dataclasses import dataclass
            
            from .models import Main, Other


            def test():
                m = Main(f=Other(val=...))

            """,
                "BetterPy: Populate arguments..."
            )
        }
    }

    fun testMissingImportAddedMore() {
        myFixture.addFileToProject(
            "models.py",
            """
            from dataclasses import dataclass
            
            
            @dataclass
            class C:
                x: int | None
                z: int = 1
            
            
            @dataclass
            class B:
                c: C
            
            
            @dataclass
            class Main:
                b: B
            """.trimIndent()
        )

        withPopulatePopupSelection(index = 2) {
            myFixture.doIntentionTest(
                "main.py",
                """
            from dataclasses import dataclass
            
            from .models import Main
            
            
            def test():
                m = Main(<caret>)
            """,
                """
            from dataclasses import dataclass
            
            from .models import Main, B, C


            def test():
                m = Main(b=B(c=C(x=..., z=...)))

            """,
                "BetterPy: Populate arguments..."
            )
        }
    }
}
