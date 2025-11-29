package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase
import fixtures.doIntentionTest

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
            "Populate missing arguments recursively"
        )
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
            "Populate missing arguments recursively"
        )
    }
}
