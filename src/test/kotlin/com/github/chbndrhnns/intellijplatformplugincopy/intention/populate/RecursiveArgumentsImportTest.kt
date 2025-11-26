package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase

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

        myFixture.configureByText(
            "main.py",
            """
            from dataclasses import dataclass
            
            from .models import Main


            def test():
                m = Main(<caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate missing arguments recursively")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from dataclasses import dataclass
            
            from .models import Main, Other


            def test():
                m = Main(f=Other(val=...))

            """.trimIndent()
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

        myFixture.configureByText(
            "main.py",
            """
            from dataclasses import dataclass
            
            from .models import Main
            
            
            def test():
                m = Main(<caret>)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Populate missing arguments recursively")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from dataclasses import dataclass
            
            from .models import Main, B, C


            def test():
                m = Main(b=B(c=C(x=..., z=...)))

            """.trimIndent()
        )
    }
}
