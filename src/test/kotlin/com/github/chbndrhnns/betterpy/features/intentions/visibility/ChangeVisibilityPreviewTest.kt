package com.github.chbndrhnns.betterpy.features.intentions.visibility

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class ChangeVisibilityPreviewTest : TestBase() {

    // Test subclass to expose the protected method
    class TestVisibilityIntention : PyToggleVisibilityIntention() {
        override fun getText(): String = "Test Intention"
        override fun getFamilyName(): String = "Test Family"
        override fun isAvailableForName(name: String): Boolean = true
        override fun calcNewName(name: String): String? = if (name.startsWith("_")) name.substring(1) else "_$name"

        public override fun shouldShowPreview(element: PsiNamedElement, newName: String): Boolean {
            return super.shouldShowPreview(element, newName)
        }
    }

    fun testShouldShowPreview_PublicToPrivate_LocalReferences() {
        myFixture.configureByText(
            "a.py", """
            def pu<caret>blic():
                pass
                
            def usage():
                public()
        """.trimIndent()
        )

        val element =
            PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), PyFunction::class.java)!!
        val intention = TestVisibilityIntention()

        // New behavior: false because only local references
        assertFalse(intention.shouldShowPreview(element, "_public"))
    }

    fun testShouldShowPreview_PublicToPrivate_NoReferences() {
        myFixture.configureByText(
            "a.py", """
            def pu<caret>blic():
                pass
        """.trimIndent()
        )

        val element =
            PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), PyFunction::class.java)!!
        val intention = TestVisibilityIntention()

        // New behavior: false because no references (so no external references)
        assertFalse(intention.shouldShowPreview(element, "_public"))
    }

    fun testShouldShowPreview_PublicToPrivate_ExternalReferences() {
        myFixture.configureByText(
            "b.py", """
            from a import public
            public()
        """.trimIndent()
        )

        myFixture.configureByText(
            "a.py", """
            def pu<caret>blic():
                pass
        """.trimIndent()
        )

        val element =
            PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), PyFunction::class.java)!!
        val intention = TestVisibilityIntention()

        assertTrue(intention.shouldShowPreview(element, "_public"))
    }

    fun testShouldShowPreview_PublicToPrivate_ReferenceOutsideClassInSameFile() {
        myFixture.configureByText(
            "a.py", """
            class C:
                def __init__(self):
                    self.b<caret>la = None

                def do(self):
                    return self.bla

            def test_():
                assert C().bla
        """.trimIndent()
        )

        val element = PsiTreeUtil.getParentOfType(
            myFixture.file.findElementAt(myFixture.caretOffset),
            com.jetbrains.python.psi.PyTargetExpression::class.java
        )!!
        val intention = TestVisibilityIntention()

        // Should return true because usage in test_ is outside class C
        assertTrue(intention.shouldShowPreview(element, "_bla"))
    }
}
