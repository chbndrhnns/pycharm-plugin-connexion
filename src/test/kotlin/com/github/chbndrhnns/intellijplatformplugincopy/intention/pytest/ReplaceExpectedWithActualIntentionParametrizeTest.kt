package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.services.DiffData
import com.github.chbndrhnns.intellijplatformplugincopy.services.TestFailureState
import com.intellij.openapi.roots.ProjectRootManager
import fixtures.TestBase

class ReplaceExpectedWithActualIntentionParametrizeTest : TestBase() {

    override fun setUp() {
        super.setUp()
        TestFailureState.getInstance(myFixture.project).clearAll()
    }

    private fun setDiffData(qName: String, expected: String, actual: String) {
        val file = myFixture.file
        val root = ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(file.virtualFile) 
                   ?: ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file.virtualFile)
        val path = root?.path ?: ""
        val key = "python<$path>://$qName"
        TestFailureState.getInstance(project).setDiffData(key, DiffData(expected, actual))
    }

    private fun buildKey(qName: String): String {
        val file = myFixture.file
        val root = ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(file.virtualFile)
                   ?: ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file.virtualFile)
        val path = root?.path ?: ""
        return "python<$path>://$qName"
    }

    fun `test intention supports parametrized test failure`() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize("arg, expected", [("abc", "defg")])
            def test_str(arg, expected):
                assert arg <caret>== expected
        """.trimIndent()
        )

        // Simulate failure for specific parameter set
        // qName usually includes [param]
        // Base qName: test_param.test_str
        // Parametrized: test_param.test_str[abc-defg] (pytest naming convention varies but this is typical)
        setDiffData("test_param.test_str[abc-defg]", "defg", "abc")

        // Currently this should fail because the intention looks for exact match on "test_param.test_str"
        val intention = myFixture.getAvailableIntention("Use actual test outcome")
        assertNotNull("Intention should be available for parametrized test failure", intention)
        
        myFixture.launchAction(intention!!)
        
        myFixture.checkResult("""
            import pytest
            
            @pytest.mark.parametrize("arg, expected", [("abc", "abc")])
            def test_str(arg, expected):
                assert arg == expected
        """.trimIndent())
    }

    fun `test intention supports parametrized test with multiple parameter sets`() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize("arg,expected", [("abc", "defg"), ("defg", "defg"), ])
            def test_str(arg, expected):
                assert arg <caret>== expected
        """.trimIndent()
        )

        // Simulate failure for the first parameter set
        setDiffData("test_param.test_str[abc-defg]", "defg", "abc")

        val intention = myFixture.getAvailableIntention("Use actual test outcome")
        assertNotNull("Intention should be available for parametrized test failure", intention)
        
        myFixture.launchAction(intention!!)
        
        myFixture.checkResult("""
            import pytest
            
            @pytest.mark.parametrize("arg,expected", [("abc", "abc"), ("defg", "defg"), ])
            def test_str(arg, expected):
                assert arg == expected
        """.trimIndent())
    }

    fun `test intention prefers explicit test key when multiple parametrized diffs exist`() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest

            @pytest.mark.parametrize("arg,expected", [("abc", "defg"), ("xxx", "yyy"), ])
            def test_str(arg, expected):
                assert arg <caret>== expected
        """.trimIndent()
        )

        setDiffData("test_param.test_str[abc-defg]", "defg", "abc")
        setDiffData("test_param.test_str[xxx-yyy]", "yyy", "xxx")

        val intention = ReplaceExpectedWithActualIntention()
        val explicitKey = buildKey("test_param.test_str[xxx-yyy]")
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            intention.invokeWithTestKey(project, myFixture.editor, myFixture.file, explicitKey)
        }

        myFixture.checkResult(
            """
            import pytest

            @pytest.mark.parametrize("arg,expected", [("abc", "defg"), ("xxx", "xxx"), ])
            def test_str(arg, expected):
                assert arg == expected
            """.trimIndent()
        )
    }
}
