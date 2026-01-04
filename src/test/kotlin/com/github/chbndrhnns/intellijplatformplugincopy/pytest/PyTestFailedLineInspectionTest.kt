package com.github.chbndrhnns.intellijplatformplugincopy.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.pytest.failedline.PyTestFailedLineInspection
import com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome.PytestLocationUrlFactory
import com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome.TestFailureListener
import com.intellij.execution.TestStateStorage
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import fixtures.TestBase
import java.util.*

class PyTestFailedLineInspectionTest : TestBase() {

    fun testFailedLineUpdatedByListener() {
        val fileText = """
            def test_fail():
                <warning descr="Test failed">assert False</warning>
        """.trimIndent()
        myFixture.configureByText("test_listener.py", fileText)

        val function = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            myFixture.file,
            com.jetbrains.python.psi.PyFunction::class.java
        ).first { it.name == "test_fail" }
        val url = PytestLocationUrlFactory.fromPyFunction(function)!!

        // Initially record has failedLine = -1
        val initialRecord = TestStateStorage.Record(1, Date(), 0, -1, "AssertionError", null, null)
        TestStateStorage.getInstance(project).writeState(url, initialRecord)

        // Mock SMTestProxy
        val proxy = SMTestProxy("test_fail", false, url)
        proxy.setTestFailed("AssertionError", """
            File "test_listener.py", line 2, in test_fail
              assert False
        """.trimIndent(), true)

        // Trigger listener
        val listener = TestFailureListener(project)
        listener.onTestFailed(proxy)

        // Verify record is updated
        val updatedRecord = TestStateStorage.getInstance(project).getState(url)!!
        assertEquals(2, updatedRecord.failedLine)

        myFixture.enableInspections(PyTestFailedLineInspection::class.java)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testHighlightFailedLine() {
        val fileText = """
            def test_fail():
                <warning descr="Test failed">assert False  # line 2</warning>
        """.trimIndent()
        myFixture.configureByText("test_highlight.py", fileText)

        val function = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            myFixture.file,
            com.jetbrains.python.psi.PyFunction::class.java
        ).first { it.name == "test_fail" }
        val url = PytestLocationUrlFactory.fromPyFunction(function)!!
        val record = TestStateStorage.Record(1, Date(), 0, 2, null, null, null)

        TestStateStorage.getInstance(project).writeState(url, record)

        myFixture.enableInspections(PyTestFailedLineInspection::class.java)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testHighlightFailedLineInMethod() {
        val fileText = """
            class TestClass:
                def test_fail(self):
                    <warning descr="Test failed">assert False</warning>
        """.trimIndent()
        myFixture.configureByText("test_method.py", fileText)

        val function = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            myFixture.file,
            com.jetbrains.python.psi.PyFunction::class.java
        ).first { it.name == "test_fail" }
        val url = PytestLocationUrlFactory.fromPyFunction(function)!!
        val record = TestStateStorage.Record(1, Date(), 0, 3, null, null, null)
        TestStateStorage.getInstance(project).writeState(url, record)

        myFixture.enableInspections(PyTestFailedLineInspection::class.java)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testSettingsToggle() {
        val fileText = """
            def test_fail():
                assert False
        """.trimIndent()
        myFixture.configureByText("test_toggle.py", fileText)

        val function = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            myFixture.file,
            com.jetbrains.python.psi.PyFunction::class.java
        ).first { it.name == "test_fail" }
        val url = PytestLocationUrlFactory.fromPyFunction(function)!!
        val record = TestStateStorage.Record(1, Date(), 0, 2, null, null, null)
        TestStateStorage.getInstance(project).writeState(url, record)

        com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState.instance().state.enableHighlightFailedTestLine =
            false
        myFixture.enableInspections(PyTestFailedLineInspection::class.java)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testHighlightFailedLineNested() {
        val file = myFixture.addFileToProject(
            "tests/test_pkg/test_file.py", """
            def test_fail():
                assert False
        """.trimIndent()
        ).virtualFile
        myFixture.configureFromExistingVirtualFile(file)

        val manager = com.intellij.testIntegration.TestFailedLineManager.getInstance(project)
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val function = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            elementAtCaret,
            com.jetbrains.python.psi.PyFunction::class.java
        )!!

        val getTestUrlMethod =
            manager.javaClass.getDeclaredMethod("getTestUrl", com.jetbrains.python.psi.PyFunction::class.java)
        getTestUrlMethod.isAccessible = true
        val url = getTestUrlMethod.invoke(manager, function) as String

        val record = TestStateStorage.Record(1, Date(), 0, 2, null, null, null)
        TestStateStorage.getInstance(project).writeState(url, record)

        myFixture.saveText(
            file, """
            def test_fail():
                <warning descr="Test failed">assert False</warning>
        """.trimIndent()
        )

        myFixture.enableInspections(PyTestFailedLineInspection::class.java)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testGetTestUrlNested() {
        val file = myFixture.addFileToProject(
            "tests/test_single_fail.py", """
            def test_fail():
                assert False
        """.trimIndent()
        ).virtualFile
        myFixture.configureFromExistingVirtualFile(file)

        val manager = com.intellij.testIntegration.TestFailedLineManager.getInstance(project)
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val function = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            elementAtCaret,
            com.jetbrains.python.psi.PyFunction::class.java
        )!!

        val getTestUrlMethod =
            manager.javaClass.getDeclaredMethod("getTestUrl", com.jetbrains.python.psi.PyFunction::class.java)
        getTestUrlMethod.isAccessible = true
        val url = getTestUrlMethod.invoke(manager, function) as String

        // Expected format: python<.../tests>://tests.test_single_fail.test_fail
        val expectedRoot = file.parent.path
        val expectedUrl = "python<$expectedRoot>://tests.test_single_fail.test_fail"
        assertEquals(expectedUrl, url)
    }
}
