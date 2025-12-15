package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.RenameDialogInterceptor
import com.intellij.ui.UiInterceptors
import fixtures.TestBase

class PytestParametrizeTest : TestBase() {

    fun testParametrizeDecorator_WrapsListItems() {
        UiInterceptors.register(RenameDialogInterceptor("Arg"))

        myFixture.configureByText(
            "test_a.py",
            """
            import pytest
            
            
            @pytest.mark.parametrize("arg", [1, 2, 3])
            def test_(arg):
                assert ar<caret>g
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.availableIntentions.find { it.text.startsWith("Introduce custom type from int") }
            ?: throw AssertionError("Intention not found")
        myFixture.launchAction(intention)

        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val actual = myFixture.file.text

        // Verify the key parts are present
        assertTrue("Should contain Arg class", actual.contains("class Arg(int):"))
        assertTrue("List items should be wrapped", actual.contains("Arg(1)"))
        assertTrue("List items should be wrapped", actual.contains("Arg(2)"))
        assertTrue("List items should be wrapped", actual.contains("Arg(3)"))
        assertTrue("Should contain parametrize decorator", actual.contains("@pytest.mark.parametrize"))

        // Verify the expression in assert is NOT wrapped (should be just "arg", not "Arg(arg)")
        assertTrue("Assert should reference arg directly", actual.contains("assert arg"))
        assertFalse("Assert should NOT wrap arg", actual.contains("assert Arg(arg)"))
    }

    fun testParametrizeDecorator_CaretOnParameterName_AddsAnnotation() {
        UiInterceptors.register(RenameDialogInterceptor("Arg"))

        myFixture.configureByText(
            "test_a.py",
            """
            import pytest
            
            
            @pytest.mark.parametrize("arg", [1, 2, 3])
            def test_(a<caret>rg):
                assert arg
            """.trimIndent()
        )
        myFixture.doHighlighting()
        val intention = myFixture.availableIntentions.find { it.text.startsWith("Introduce custom type from int") }
            ?: throw AssertionError("Intention not found")
        myFixture.launchAction(intention)

        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val actual = myFixture.file.text

        // Verify the key parts are present
        assertTrue("Should contain Arg class", actual.contains("class Arg(int):"))
        assertTrue("List items should be wrapped", actual.contains("Arg(1)"))
        assertTrue("List items should be wrapped", actual.contains("Arg(2)"))
        assertTrue("List items should be wrapped", actual.contains("Arg(3)"))

        // Verify parameter has annotation, not wrapped
        assertTrue("Parameter should have annotation", actual.contains("def test_(arg: Arg):"))
        assertFalse("Parameter should NOT be wrapped", actual.contains("def test_(Arg(arg))"))
        assertFalse("Parameter should NOT be wrapped", actual.contains("def test_(CustomInt(arg))"))
    }

}
