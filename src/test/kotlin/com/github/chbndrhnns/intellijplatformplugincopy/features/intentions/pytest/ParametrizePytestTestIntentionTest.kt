package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.pytest

import fixtures.TestBase

class ParametrizePytestTestIntentionTest : TestBase() {

    fun testParametrizeTopLevelFunctionNoArgs() {
        myFixture.configureByText(
            "test_foo.py",
            """
            def test_so<caret>mething():
                assert True
            """.trimIndent(),
        )

        val intention = myFixture.findSingleIntention("BetterPy: Parametrize pytest test")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest


            @pytest.mark.parametrize("arg", [])
            def test_something(arg):
                assert True
        """.trimIndent()
        )
    }

    fun testParametrizeTopLevelFunction() {
        myFixture.configureByText(
            "test_foo.py",
            """
            def test_so<caret>mething(some_fixture):
                assert True
            """.trimIndent(),
        )

        val intention = myFixture.findSingleIntention("BetterPy: Parametrize pytest test")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest


            @pytest.mark.parametrize("arg", [])
            def test_something(arg, some_fixture):
                assert True
        """.trimIndent()
        )
    }

    fun testParametrizeInsideTestClass() {
        myFixture.configureByText(
            "test_foo.py",
            """
            class TestClass:
                def test_so<caret>mething(self, some_fixture):
                    assert True
            """.trimIndent(),
        )

        val intention = myFixture.findSingleIntention("BetterPy: Parametrize pytest test")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            
            class TestClass:
                @pytest.mark.parametrize("arg", [])
                def test_something(self, arg, some_fixture):
                    assert True
        """.trimIndent()
        )
    }

    fun testParametrizeInsideInnerClass() {
        myFixture.configureByText(
            "test_foo.py",
            """
            class TestOuter:
                class Inner:
                    def test_so<caret>mething(self, some_fixture):
                        assert True
            """.trimIndent(),
        )

        val intention = myFixture.findSingleIntention("BetterPy: Parametrize pytest test")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest
            
            
            class TestOuter:
                class Inner:
                    @pytest.mark.parametrize("arg", [])
                    def test_something(self, arg, some_fixture):
                        assert True
        """.trimIndent()
        )
    }

    fun testDoesNotOfferWhenAlreadyParametrized() {
        myFixture.configureByText(
            "test_foo.py",
            """
            import pytest

            @pytest.mark.parametrize("arg", [])
            def test_so<caret>mething(arg, some_fixture):
                assert True
            """.trimIndent(),
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Parametrize pytest test")
        assertTrue(intentions.isEmpty())
    }

    fun testNotAvailableForNonTestFunction() {
        myFixture.configureByText(
            "foo.py",
            """
            def so<caret>mething():
                assert True
            """.trimIndent(),
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Parametrize pytest test")
        assertTrue(intentions.isEmpty())
    }

    fun testAddsImportIfMissing() {
        myFixture.configureByText(
            "test_foo.py",
            """
            def test_so<caret>mething(some_fixture):
                assert True
            """.trimIndent(),
        )

        val intention = myFixture.findSingleIntention("BetterPy: Parametrize pytest test")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest


            @pytest.mark.parametrize("arg", [])
            def test_something(arg, some_fixture):
                assert True
        """.trimIndent()
        )
    }
}
