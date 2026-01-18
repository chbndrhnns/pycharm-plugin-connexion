package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class MoveTestToClassRefactoringTest : TestBase() {

    private val intentionName = PluginConstants.ACTION_PREFIX + "Move test to another class"

    fun testMoveToNewClass() {
        myFixture.doIntentionTest(
            "test_move.py",
            """
            class TestOld:
                <caret>def test_new_feature(self):
                    pass
            """,
            """
            class TestOld:
                pass


            class TestNewFeature:
                def test_new_feature(self):
                    pass
            """,
            intentionName,
            dialogOk = true
        )
    }

    fun testMoveOneOfManyToNewClass() {
        myFixture.doIntentionTest(
            "test_move_many.py",
            """
            class TestOld:
                def test_keep(self):
                    pass

                <caret>def test_move(self):
                    pass
            """,
            """
            class TestOld:
                def test_keep(self):
                    pass


            class TestMove:
                def test_move(self):
                    pass
            """,
            intentionName,
            dialogOk = true
        )
    }

    fun testNotAvailableInBody() {
        myFixture.assertIntentionNotAvailable(
            "test_not_available.py",
            """
            class TestOld:
                def test_foo(self):
                    <caret>pass
            """,
            intentionName
        )
    }

    fun testMoveAsyncTestToNewClass() {
        myFixture.doIntentionTest(
            "test_move_async.py",
            """
            class TestOld:
                <caret>async def test_async_feature(self):
                    pass
            """,
            """
            class TestOld:
                pass


            class TestAsyncFeature:
                async def test_async_feature(self):
                    pass
            """,
            intentionName,
            dialogOk = true
        )
    }
}
