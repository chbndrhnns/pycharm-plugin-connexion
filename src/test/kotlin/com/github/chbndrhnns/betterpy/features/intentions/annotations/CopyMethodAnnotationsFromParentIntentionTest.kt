package com.github.chbndrhnns.betterpy.features.intentions.annotations

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.ui.UiInterceptors
import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class CopyMethodAnnotationsFromParentIntentionTest : TestBase() {

    private val copyToSubclasses = "BetterPy: Copy type annotations to subclasses"
    private val copyFromParent = "BetterPy: Copy type annotations from parent"

    fun testCopiesParentAnnotationsToOverrides() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class Base:
                def met<caret>hod(self, a: int, b: str) -> bool:
                    pass

            class Child(Base):
                def method(self, a, b):
                    pass

            class Sibling(Base):
                def method(self, a: int, b: str) -> bool:
                    pass
            """,
            """
            class Base:
                def method(self, a: int, b: str) -> bool:
                    pass

            class Child(Base):
                def method(self, a: int, b: str) -> bool:
                    pass

            class Sibling(Base):
                def method(self, a: int, b: str) -> bool:
                    pass
            """,
            copyToSubclasses
        )
    }

    fun testCopiesParentAnnotationsWhenOnOverride() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class Base:
                def method(self, value: str) -> int:
                    pass

            class Child(Base):
                def met<caret>hod(self, value):
                    pass
            """,
            """
            class Base:
                def method(self, value: str) -> int:
                    pass

            class Child(Base):
                def method(self, value: str) -> int:
                    pass
            """,
            copyFromParent
        )
    }

    fun testConflictsShownAndIgnoredWhenOverwritingAnnotations() {
        val before = """
            class Base:
                def method(self, payload: int) -> bool:
                    pass

            class Child(Base):
                def met<caret>hod(self, payload: str) -> int:
                    pass
            """
        myFixture.configureByText("a.py", before.trimIndent())
        myFixture.doHighlighting()

        var dialogShown = false
        UiInterceptors.register(object :
            UiInterceptors.UiInterceptor<ConflictsDialog>(ConflictsDialog::class.java) {
            override fun doIntercept(component: ConflictsDialog) {
                dialogShown = true
                component.close(DialogWrapper.OK_EXIT_CODE)
            }
        })

        try {
            val intention = myFixture.findSingleIntention(copyFromParent)
            myFixture.launchAction(intention)

            assertTrue("Expected conflicts warning dialog to be shown", dialogShown)

            var expected = before.trimIndent()
            if (!expected.endsWith("\n") && myFixture.file.text.endsWith("\n")) {
                expected += "\n"
            }
            myFixture.checkResult(expected)
        } finally {
            UiInterceptors.clear()
        }
    }

    fun testCopiesAnnotationsForArgsAndKwargs() {
        myFixture.doIntentionTest(
            "a.py",
            """
            class Base:
                def met<caret>hod(self, *args: int, **kwargs: str) -> bool:
                    pass

            class Child(Base):
                def method(self, *args, **kwargs):
                    pass
            """,
            """
            class Base:
                def method(self, *args: int, **kwargs: str) -> bool:
                    pass

            class Child(Base):
                def method(self, *args: int, **kwargs: str) -> bool:
                    pass
            """,
            copyToSubclasses
        )
    }

    fun testIntentionHiddenWhenDisabled() {
        withPluginSettings({ enableCopyMethodAnnotationsFromParentIntention = false }) {
            myFixture.assertIntentionNotAvailable(
                "a.py",
                """
                class Base:
                    def met<caret>hod(self, value: int) -> str:
                        pass

                class Child(Base):
                    def method(self, value):
                        pass
                """,
                copyToSubclasses
            )
        }
    }
}
