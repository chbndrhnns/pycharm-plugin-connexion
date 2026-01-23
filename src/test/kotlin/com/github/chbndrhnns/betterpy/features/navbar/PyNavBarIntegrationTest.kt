package com.github.chbndrhnns.betterpy.features.navbar

import com.intellij.ide.ui.UISettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PyNavBarIntegrationTest : BasePlatformTestCase() {

    private var originalShowMembers: Boolean = false

    override fun setUp() {
        super.setUp()
        originalShowMembers = UISettings.getInstance().showMembersInNavigationBar
        UISettings.getInstance().showMembersInNavigationBar = true
    }

    override fun tearDown() {
        try {
            UISettings.getInstance().showMembersInNavigationBar = originalShowMembers
        } finally {
            super.tearDown()
        }
    }

    fun testNavigationBarShowsMembers() {
        myFixture.configureByText(
            "test.py", """
            class Calculator:
                def add(self, a, b):
                    return a + <caret>b
        """.trimIndent()
        )

        // Get breadcrumbs at caret (navigation bar uses same model)
        val breadcrumbs = myFixture.getBreadcrumbsAtCaret()

        assertTrue(
            "Should show class in breadcrumbs",
            breadcrumbs.any { it.text == "Calculator" })
        assertTrue(
            "Should show method in breadcrumbs",
            breadcrumbs.any { it.text == "add()" })
    }
}
