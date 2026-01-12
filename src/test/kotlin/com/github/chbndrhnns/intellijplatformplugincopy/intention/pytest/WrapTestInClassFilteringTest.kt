package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.UiInterceptors
import fixtures.TestBase

class WrapTestInClassFilteringTest : TestBase() {

    private val intentionName = PluginConstants.ACTION_PREFIX + "Wrap test in class"

    fun testFiltersCollidingClassFromDialog() {
        myFixture.configureByText(
            "test_wrap_filtering.py",
            """
            class TestCollision:
                def test_function(self):
                    pass

            class TestSafe:
                pass

            def <caret>test_function():
                pass
            """
        )

        var dialogShown = false
        UiInterceptors.register(object :
            UiInterceptors.UiInterceptor<WrapTestInClassDialog>(WrapTestInClassDialog::class.java) {
            override fun doIntercept(component: WrapTestInClassDialog) {
                dialogShown = true

                // Inspect the ComboBox items
                val comboField = component.javaClass.getDeclaredField("existingClassComboBox")
                comboField.isAccessible = true
                val combo = comboField.get(component) as ComboBox<*>

                val items = (0 until combo.itemCount).map { combo.getItemAt(it).toString() }

                assertFalse("Should not contain colliding class 'TestCollision'", items.contains("TestCollision"))
                assertTrue("Should contain safe class 'TestSafe'", items.contains("TestSafe"))

                component.close(DialogWrapper.CANCEL_EXIT_CODE)
            }
        })

        val intention = myFixture.findSingleIntention(intentionName)
        myFixture.launchAction(intention)

        assertTrue("Dialog should have been shown", dialogShown)
    }
}
