package com.github.chbndrhnns.betterpy.features.pytest.surround

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class PytestRaisesSurrounderSettingsToggleTest : TestBase() {

    fun testSurrounderHiddenWhenDisabled() {
        withPluginSettings({ enableSurroundWithPytestRaisesIntention = false }) {
            myFixture.configureByText(
                "test_example.py",
                """
                def test_something():
                    <selection>raise ValueError()</selection>
                """.trimIndent()
            )
            assertFalse(isSelectionApplicable())
        }
    }

    fun testSurrounderVisibleWhenEnabled() {
        withPluginSettings({ enableSurroundWithPytestRaisesIntention = true }) {
            myFixture.configureByText(
                "test_example.py",
                """
                def test_something():
                    <selection>raise ValueError()</selection>
                """.trimIndent()
            )
            assertTrue(isSelectionApplicable())
        }
    }

    private fun isSelectionApplicable(): Boolean {
        val descriptor = PytestSurroundDescriptor()
        val selection = myFixture.editor.selectionModel
        val elements = descriptor.getElementsToSurround(
            myFixture.file,
            selection.selectionStart,
            selection.selectionEnd
        )
        val surrounder = descriptor.surrounders.single { it.templateDescription == "pytest.raises()" }
        return surrounder.isApplicable(elements)
    }
}
