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
}
