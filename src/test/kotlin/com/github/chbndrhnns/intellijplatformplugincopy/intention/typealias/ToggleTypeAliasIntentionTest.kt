package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.jetbrains.python.psi.LanguageLevel
import fixtures.PythonTestSetup
import fixtures.TestBase

class ToggleTypeAliasIntentionTest : TestBase() {
    private fun doTest(before: String, after: String, languageLevel: LanguageLevel = LanguageLevel.PYTHON311) {
        if (languageLevel != LanguageLevel.PYTHON311) {
            setLanguageLevel(languageLevel)
        }
        myFixture.configureByText("a.py", before.trimIndent())
        myFixture.launchAction(ToggleTypeAliasIntention())
        val actual = myFixture.file.text.replace("\\s".toRegex(), "")
        val expected = after.trimIndent().replace("\\s".toRegex(), "")
        assertEquals(expected, actual)
    }

    private fun setLanguageLevel(level: LanguageLevel) {
        val sdkRoot = myFixture.tempDirFixture.findOrCreateDir("sdk")
        val sdk = PythonTestSetup.createAndRegisterSdk(
            root = sdkRoot,
            disposable = testRootDisposable,
            languageLevel = level,
            addToJdkTable = true
        )
        ModuleRootModificationUtil.setModuleSdk(myFixture.module, sdk)
    }

    fun testAnnotationToAlias() {
        doTest(
            """
            def foo(x: List[i<caret>nt]):
                pass
        """, """
            MyAlias = List[int]
            def foo(x: MyAlias):
                pass
        """
        )
    }

    fun testAnnotationToAliasPy312() {
        doTest(
            """
            def foo(x: List[i<caret>nt]):
                pass
        """, """
            type MyAlias = List[int]
            def foo(x: MyAlias):
                pass
        """, LanguageLevel.PYTHON312
        )
    }

    fun testAliasToAnnotation() {
        doTest(
            """
            MyAlias = List[int]
            def foo(x: MyA<caret>lias):
                pass
            def bar(y: MyAlias):
                pass
        """, """
            def foo(x: List[int]):
                pass
            def bar(y: List[int]):
                pass
        """
        )
    }

    fun testAnnotationToAliasWithImports() {
        doTest(
            """
            import sys
            from typing import List
            
            def foo(x: List[i<caret>nt]):
                pass
        """, """
            import sys
            from typing import List
            
            MyAlias = List[int]
            def foo(x: MyAlias):
                pass
        """
        )
    }

    fun testAnnotationToAliasInReturn() {
        doTest(
            """
            def do(arg: int) -> List[i<caret>nt]:
                ...
        """, """
            MyAlias = List[int]
            def do(arg: int) -> MyAlias:
                ...
        """
        )
    }

    fun testShouldNotBeAvailableOnNormalAssignment() {
        myFixture.configureByText(
            "a.py", """
            x = 1
            print(<caret>x)
        """.trimIndent()
        )

        // It should NOT be available
        val action = ToggleTypeAliasIntention()
        val available = action.isAvailable(project, myFixture.editor, myFixture.file)
        assertFalse("Action should not be available on normal variable usage", available)
    }

    fun testShouldNotBeAvailableOnNormalAssignmentDefinition() {
        myFixture.configureByText(
            "a.py", """
            x<caret> = 1
        """.trimIndent()
        )

        val action = ToggleTypeAliasIntention()
        val available = action.isAvailable(project, myFixture.editor, myFixture.file)
        assertFalse("Action should not be available on normal variable definition", available)
    }
}
