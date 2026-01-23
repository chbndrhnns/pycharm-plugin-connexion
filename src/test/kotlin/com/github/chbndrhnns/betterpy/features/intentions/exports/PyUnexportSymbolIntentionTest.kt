package com.github.chbndrhnns.betterpy.features.intentions.exports

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import fixtures.TestBase

class PyUnexportSymbolIntentionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        PluginSettingsState.instance().state.enableUnexportSymbolIntention = true
    }

    fun testUnexportFromAll() {
        myFixture.configureByText(
            "__init__.py", """
            from ._lib import Client
            __all__ = ["Clie<caret>nt"]
        """.trimIndent()
        )

        myFixture.launchAction(myFixture.findSingleIntention("BetterPy: Remove from __all__"))

        myFixture.checkResult(
            """
            __all__ = []
        """.trimIndent()
        )
    }

    fun testUnexportFromAllWithMultipleItems() {
        myFixture.configureByText(
            "__init__.py", """
            from ._lib import Client, Other
            __all__ = ["Clie<caret>nt", "Other"]
        """.trimIndent()
        )

        myFixture.launchAction(myFixture.findSingleIntention("BetterPy: Remove from __all__"))

        myFixture.checkResult(
            """
            from ._lib import Other
            __all__ = ["Other"]
        """.trimIndent()
        )
    }

    fun testUnexportFromAllWithUsages() {
        myFixture.configureByText("other.py", "from . import Client")
        myFixture.configureByText(
            "__init__.py", """
            from ._lib import Client
            __all__ = ["Clie<caret>nt"]
        """.trimIndent()
        )

        // In unit tests, the confirmation dialog is skipped by my implementation.
        // So it should just work.
        myFixture.launchAction(myFixture.findSingleIntention("BetterPy: Remove from __all__"))

        myFixture.checkResult("__all__ = []")
    }

    fun testUnexportFromAllLeavesOtherImports() {
        myFixture.configureByText(
            "__init__.py", """
            from ._lib import Client, Other
            __all__ = ["Clie<caret>nt", "Other"]
        """.trimIndent()
        )

        myFixture.launchAction(myFixture.findSingleIntention("BetterPy: Remove from __all__"))

        myFixture.checkResult(
            """
            from ._lib import Other
            __all__ = ["Other"]
        """.trimIndent()
        )
    }

    fun testUnexportFromAllWithCaretAtBeginningOfList() {
        myFixture.configureByText(
            "__init__.py", """
            from ._lib import Client
            __all__ = [<caret>"Client"]
        """.trimIndent()
        )

        myFixture.launchAction(myFixture.findSingleIntention("BetterPy: Remove from __all__"))

        myFixture.checkResult("__all__ = []")
    }
}
