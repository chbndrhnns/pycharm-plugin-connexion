package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import fixtures.TestBase
import fixtures.doRefactoringActionTest

class WrapTestInClassRefactoringCollisionTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest.WrapTestInClassRefactoringAction"

    fun testWrapTestFunctionWithNameCollision() {
        myFixture.doRefactoringActionTest(
            "test_foo.py",
            """
            class TestUserLogin:
                pass

            def test_user_login():
                <caret>assert True
            """,
            """
            class TestUserLogin:
                pass


            class TestUserLogin1:
                def test_user_login(self):
                    assert True
            """,
            actionId,
            dialogOk = true
        )
    }
}
