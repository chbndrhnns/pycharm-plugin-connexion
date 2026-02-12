package com.github.chbndrhnns.betterpy.features.intentions.pytest

import fixtures.TestBase

class ChangeParametrizeSignatureTest : TestBase() {

    // --- Add parameter tests ---

    fun testAddParameterToCommaDelimitedString() {
        myFixture.configureByText(
            "test_add.py", """
            import pytest
            
            @pytest.mark.parametrize("arg1, ar<caret>g2", [(1, 2), (3, 4)])
            def test_something(arg1, arg2):
                pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Add parametrize parameter")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("arg1, arg2, arg3", [(1, 2, None), (3, 4, None)])
            def test_something(arg1, arg2, arg3):
                pass
        """.trimIndent()
        )
    }

    fun testAddParameterToTupleOfStrings() {
        myFixture.configureByText(
            "test_add.py", """
            import pytest
            
            @pytest.mark.parametrize(("ar<caret>g1", "arg2"), [(1, 2), (3, 4)])
            def test_something(arg1, arg2):
                pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Add parametrize parameter")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize(("arg1", "arg2", "arg3"), [(1, 2, None), (3, 4, None)])
            def test_something(arg1, arg2, arg3):
                pass
        """.trimIndent()
        )
    }

    fun testAddParameterToSingleParam() {
        myFixture.configureByText(
            "test_add.py", """
            import pytest
            
            @pytest.mark.parametrize("ar<caret>g1", [1, 2, 3])
            def test_something(arg1):
                pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Add parametrize parameter")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("arg1, arg2", [(1, None), (2, None), (3, None)])
            def test_something(arg1, arg2):
                pass
        """.trimIndent()
        )
    }

    fun testAddParameterWithPytestParam() {
        myFixture.configureByText(
            "test_add.py", """
            import pytest
            
            @pytest.mark.parametrize("arg1, ar<caret>g2", [pytest.param(1, 2, id="a"), pytest.param(3, 4, id="b")])
            def test_something(arg1, arg2):
                pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Add parametrize parameter")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("arg1, arg2, arg3", [pytest.param(1, 2, None, id="a"), pytest.param(3, 4, None, id="b")])
            def test_something(arg1, arg2, arg3):
                pass
        """.trimIndent()
        )
    }

    // --- Remove parameter tests ---

    fun testRemoveParameterFromCommaDelimitedString() {
        myFixture.configureByText(
            "test_remove.py", """
            import pytest
            
            @pytest.mark.parametrize("arg1, ar<caret>g2, arg3", [(1, 2, 3), (4, 5, 6)])
            def test_something(arg1, arg2, arg3):
                pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Remove parametrize parameter")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("arg1, arg3", [(1, 3), (4, 6)])
            def test_something(arg1, arg3):
                pass
        """.trimIndent()
        )
    }

    fun testRemoveParameterFromTupleOfStrings() {
        myFixture.configureByText(
            "test_remove.py", """
            import pytest
            
            @pytest.mark.parametrize(("arg1", "ar<caret>g2", "arg3"), [(1, 2, 3), (4, 5, 6)])
            def test_something(arg1, arg2, arg3):
                pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Remove parametrize parameter")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize(("arg1", "arg3"), [(1, 3), (4, 6)])
            def test_something(arg1, arg3):
                pass
        """.trimIndent()
        )
    }

    fun testRemoveLastOfTwoParamsCollapsesToSingleParam() {
        myFixture.configureByText(
            "test_remove.py", """
            import pytest
            
            @pytest.mark.parametrize("arg1, ar<caret>g2", [(1, 2), (3, 4)])
            def test_something(arg1, arg2):
                pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Remove parametrize parameter")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("arg1,", [(1,), (3,)])
            def test_something(arg1):
                pass
        """.trimIndent()
        )
    }

    fun testRemoveParameterWithPytestParam() {
        myFixture.configureByText(
            "test_remove.py", """
            import pytest
            
            @pytest.mark.parametrize("arg1, ar<caret>g2, arg3", [pytest.param(1, 2, 3, id="a"), pytest.param(4, 5, 6, id="b")])
            def test_something(arg1, arg2, arg3):
                pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Remove parametrize parameter")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            @pytest.mark.parametrize("arg1, arg3", [pytest.param(1, 3, id="a"), pytest.param(4, 6, id="b")])
            def test_something(arg1, arg3):
                pass
        """.trimIndent()
        )
    }

    fun testRemoveNotAvailableForSingleParam() {
        myFixture.configureByText(
            "test_remove.py", """
            import pytest
            
            @pytest.mark.parametrize("ar<caret>g1", [1, 2, 3])
            def test_something(arg1):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Remove parametrize parameter")
        assertTrue("Remove should not be available for single param", intentions.isEmpty())
    }

    fun testRemoveNotAvailableForUsedParameter() {
        myFixture.configureByText(
            "test_remove.py", """
            import pytest
            
            @pytest.mark.parametrize("arg1, ar<caret>g2", [(1, 2), (3, 4)])
            def test_something(arg1, arg2):
                assert arg2 > 0
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Remove parametrize parameter")
        assertTrue("Remove should not be available for used parameter", intentions.isEmpty())
    }

    fun testNotAvailableOutsideParametrize() {
        myFixture.configureByText(
            "test_none.py", """
            import pytest
            
            def test_some<caret>thing(arg1):
                pass
        """.trimIndent()
        )

        val addIntentions = myFixture.filterAvailableIntentions("BetterPy: Add parametrize parameter")
        assertTrue("Add should not be available outside parametrize", addIntentions.isEmpty())

        val removeIntentions = myFixture.filterAvailableIntentions("BetterPy: Remove parametrize parameter")
        assertTrue("Remove should not be available outside parametrize", removeIntentions.isEmpty())
    }
}
