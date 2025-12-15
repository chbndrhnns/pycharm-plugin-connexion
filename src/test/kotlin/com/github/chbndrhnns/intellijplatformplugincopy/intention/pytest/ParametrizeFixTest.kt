package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.services.DiffData
import com.github.chbndrhnns.intellijplatformplugincopy.services.TestFailureState
import com.intellij.openapi.roots.ProjectRootManager
import fixtures.TestBase

class ParametrizeFixTest : TestBase() {

    override fun setUp() {
        super.setUp()
        TestFailureState.getInstance(myFixture.project).clearAll()
    }

    private fun setDiffData(qName: String, expected: String, actual: String) {
        val file = myFixture.file
        val root = ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(file.virtualFile) 
                   ?: ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file.virtualFile)
        
        val prefixes = listOfNotNull(
            root?.path?.let { "python<$it>://" },
            "python:"
        )
        
        // qName input is like "test_list_args.test_foo[param]"
        // We need to support cases where the function qname is detected as "test_foo" or "test_list_args.test_foo"
        // If the key is "python:...//test_list_args.test_foo[param]"
        // And the calculated base is "python:...//test_foo"
        // It won't match.
        // So we should add keys that match the shorter qname too.
        
        val variants = mutableListOf<String>()
        variants.add(qName)
        
        // specific hack for this test structure: remove module prefix if present
        if (qName.contains(".")) {
             // e.g. test_list_args.test_foo[param] -> test_foo[param]
             // But be careful about split.
             // We want to remove the file name part.
             val parts = qName.split(".")
             if (parts.size >= 2) {
                 // heuristic: take last part (func name + params)
                 variants.add(parts.last()) 
             }
        }
        
        val state = TestFailureState.getInstance(project)
        for (prefix in prefixes) {
            for (variant in variants) {
                val key = "$prefix$variant"
                state.setDiffData(key, DiffData(expected, actual))
            }
        }
    }

    fun `test parametrize with list of argnames`() {
        myFixture.configureByText(
            "test_list_args.py", """
            import pytest
            
            @pytest.mark.parametrize(["arg", "expected"], [
                ("input", "exp")
            ])
            def test_foo(arg, expected):
                assert arg == expec<caret>ted
        """.trimIndent()
        )

        // Mock failure
        setDiffData("test_list_args.test_foo[input-exp]", "exp", "input")

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult("""
            import pytest
            
            @pytest.mark.parametrize(["arg", "expected"], [
                ("input", "input")
            ])
            def test_foo(arg, expected):
                assert arg == expected
        """.trimIndent())
    }
    
    fun `test parametrize with keyword arguments`() {
        myFixture.configureByText(
            "test_kwargs.py", """
            import pytest
            
            @pytest.mark.parametrize(argnames="arg, expected", argvalues=[
                ("input", "exp")
            ])
            def test_kwargs(arg, expected):
                assert arg == expec<caret>ted
        """.trimIndent()
        )

        setDiffData("test_kwargs.test_kwargs[input-exp]", "exp", "input")

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult("""
            import pytest
            
            @pytest.mark.parametrize(argnames="arg, expected", argvalues=[
                ("input", "input")
            ])
            def test_kwargs(arg, expected):
                assert arg == expected
        """.trimIndent())
    }

    fun `test parametrize with extra arguments and keyword mix`() {
         myFixture.configureByText(
            "test_extra.py", """
            import pytest
            
            @pytest.mark.parametrize("expected", ["exp"], ids=["case1"])
            def test_extra(expected):
                assert "actual" == expec<caret>ted
        """.trimIndent()
        )

        setDiffData("test_extra.test_extra[case1]", "exp", "actual")

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult("""
            import pytest
            
            @pytest.mark.parametrize("expected", ["actual"], ids=["case1"])
            def test_extra(expected):
                assert "actual" == expected
        """.trimIndent())
    }
    
    fun `test parametrize with integer values`() {
         myFixture.configureByText(
            "test_int.py", """
            import pytest
            
            @pytest.mark.parametrize("expected", [1])
            def test_int(expected):
                assert 2 == expec<caret>ted
        """.trimIndent()
        )

        setDiffData("test_int.test_int[1]", "1", "2")

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult("""
            import pytest
            
            @pytest.mark.parametrize("expected", [2])
            def test_int(expected):
                assert 2 == expected
        """.trimIndent())
    }

    fun `test parametrize replaces dict literal in decorator`() {
        myFixture.configureByText(
            "test_dict.py", """
            import pytest

            @pytest.mark.parametrize("arg,exp", [({"abc": 1}, {"abc": 2})])
            def test_(arg, exp):
                assert arg == e<caret>xp
        """.trimIndent()
        )

        setDiffData("test_dict.test_[{'abc': 1}-{'abc': 2}]", "{'abc': 2}", "{'abc': 1}")

        val intention = myFixture.findSingleIntention("Use actual test outcome")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import pytest

            @pytest.mark.parametrize("arg,exp", [({"abc": 1}, {'abc': 1})])
            def test_(arg, exp):
                assert arg == exp
            """.trimIndent()
        )
    }
}
