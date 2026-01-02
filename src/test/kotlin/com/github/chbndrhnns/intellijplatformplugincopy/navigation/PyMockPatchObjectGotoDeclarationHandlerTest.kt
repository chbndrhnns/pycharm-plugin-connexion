package com.github.chbndrhnns.intellijplatformplugincopy.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PyMockPatchObjectGotoDeclarationHandlerTest : TestBase() {

    fun testGotoDeclarationOnPatchObjectAttribute() {
        myFixture.configureByText(
            "a.py",
            """
            from unittest.mock import patch
            
            class ExternalService:
                def fetch_data(self):
                    pass
            
            with patch.object(ExternalService, "fetch_d<caret>ata"):
                pass
            """.trimIndent()
        )

        val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)

        assertNotNull(targets)
        assertEquals(1, targets.size)
        val target = targets[0]
        assertTrue(target is PyFunction)
        assertEquals("fetch_data", (target as PyFunction).name)
    }

    fun testGotoDeclarationOnPatchObjectAttributeAsDecorator() {
        myFixture.configureByText(
            "a.py",
            """
            from unittest.mock import patch
            
            class MyService:
                name: str = "test"
            
            @patch.object(MyService, "na<caret>me")
            def test_something():
                pass
            """.trimIndent()
        )

        val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)

        assertNotNull(targets)
        assertTrue(targets.isNotEmpty())
    }

    fun testNoTargetsForNonExistentAttribute() {
        myFixture.configureByText(
            "a.py",
            """
            from unittest.mock import patch
            
            class MyService:
                def existing_method(self):
                    pass
            
            with patch.object(MyService, "non_exist<caret>ing"):
                pass
            """.trimIndent()
        )

        val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)

        assertTrue(targets.isEmpty())
    }
}
