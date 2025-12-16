package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import fixtures.TestBase

class UseActualTestOutcomeFromTreeActionTest : TestBase() {

    fun `test action update is only enabled for leaf failing test`() {
        val action = UseActualTestOutcomeFromTreeAction()

        val leaf = SMTestProxy("test_foo", false, "python://test")
        leaf.setTestFailed("msg", "stack", false)

        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(AbstractTestProxy.DATA_KEYS, arrayOf<AbstractTestProxy>(leaf))
            .build()
        val event = TestActionEvent.createTestEvent(action, dataContext)

        action.update(event)
        assertTrue(event.presentation.isEnabledAndVisible)

        val suite = SMTestProxy("test_foo", true, "python://test")
        suite.addChild(leaf)
        suite.setTestFailed("msg", "stack", false)

        val suiteDataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(AbstractTestProxy.DATA_KEYS, arrayOf<AbstractTestProxy>(suite))
            .build()
        val suiteEvent = TestActionEvent.createTestEvent(action, suiteDataContext)

        action.update(suiteEvent)
        assertFalse(suiteEvent.presentation.isEnabledAndVisible)
    }

    fun `test extract line number from standard python stacktrace`() {
        val stacktrace = """
            Traceback (most recent call last):
              File "/Users/jo/src/project/test_file.py", line 123, in test_method
                assert 1 == 2
            AssertionError
        """.trimIndent()

        val proxy = SMTestProxy("test_method", false, "python://test")
        proxy.setTestFailed("msg", stacktrace, false)

        val file = myFixture.configureByText("test_file.py", "")

        val lineNumber = UseActualTestOutcomeFromTreeAction.getFailedLineNumber(proxy.stacktrace!!, file.virtualFile)
        assertEquals(122, lineNumber) // 123 - 1
    }

    fun `test extract line number from pytest stacktrace`() {
        val standardPytestStack = """
Traceback (most recent call last):
  File "/Users/jo/src/test_simple.py", line 6, in test_foo
    assert arg == "expected"
AssertionError: assert 'input' == 'expected'
        """.trimIndent()

        val proxy = SMTestProxy("test_foo", false, "python://test")
        proxy.setTestFailed("msg", standardPytestStack, false)

        val file = myFixture.configureByText("test_simple.py", "")

        val lineNumber = UseActualTestOutcomeFromTreeAction.getFailedLineNumber(proxy.stacktrace!!, file.virtualFile)
        assertEquals(5, lineNumber) // 6 - 1
    }

    fun `test extract line number handles multiple frames`() {
        val stacktrace = """
            Traceback (most recent call last):
              File "/lib/runner.py", line 10, in run
                test()
              File "/src/test_file.py", line 50, in test_main
                helper()
              File "/src/test_file.py", line 100, in helper
                assert False
        """.trimIndent()

        val proxy = SMTestProxy("test_main", false, "python://test")
        proxy.setTestFailed("msg", stacktrace, false)

        val file = myFixture.configureByText("test_file.py", "")

        val lineNumber = UseActualTestOutcomeFromTreeAction.getFailedLineNumber(proxy.stacktrace!!, file.virtualFile)
        assertEquals(99, lineNumber) // 100 - 1
    }

    fun `test extract line number from pytest failure summary`() {
        val stacktrace = """
/Users/jo/PyCharmMiscProject/.venv/bin/python ...
test_fail.py::test_list FAILED                                           [100%]
test_fail.py:7 (test_list)
[1] != [2]

Expected :[2]
Actual   :[1]
<Click to see difference>

def test_list():
>       assert [1] == [2]
E       assert [1] == [2]

test_fail.py:9: AssertionError
        """.trimIndent()

        val proxy = SMTestProxy("test_list", false, "python://test")
        proxy.setTestFailed("msg", stacktrace, false)

        val file = myFixture.configureByText("test_fail.py", "")

        val lineNumber = UseActualTestOutcomeFromTreeAction.getFailedLineNumber(proxy.stacktrace!!, file.virtualFile)
        assertEquals(8, lineNumber) // 9 - 1
    }
}