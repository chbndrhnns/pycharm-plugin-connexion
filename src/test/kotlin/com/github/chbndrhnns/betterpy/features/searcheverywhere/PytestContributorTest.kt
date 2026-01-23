package com.github.chbndrhnns.betterpy.features.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.psi.PsiElement
import com.intellij.util.Processor
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PytestContributorTest : TestBase() {

    fun `test resolve plain method`() {
        myFixture.addFileToProject("tests/__init__.py", "")
        myFixture.addFileToProject(
            "tests/test_foo.py", """
            class TestClass:
                def test_method(self):
                    pass
        """.trimIndent()
        )

        val contributor = PytestIdentifierContributor(project)
        val results = search(contributor, "tests/test_foo.py::TestClass::test_method")

        assertSize(1, results)
        val element = results[0].item
        assertTrue(element is PyFunction)
        assertEquals("test_method", (element as PyFunction).name)
    }

    fun `test resolve parametrized`() {
        myFixture.addFileToProject("tests/__init__.py", "")
        myFixture.addFileToProject(
            "tests/test_bar.py", """
            def test_param():
                pass
        """.trimIndent()
        )

        val contributor = PytestIdentifierContributor(project)
        // Simulate input with parametrization suffix
        val results = search(contributor, "tests/test_bar.py::test_param[1-2-3]")

        assertSize(1, results)
        val element = results[0].item
        assertTrue(element is PyFunction)
        assertEquals("test_param", (element as PyFunction).name)
    }

    fun `test resolve inner class`() {
        myFixture.addFileToProject("tests/__init__.py", "")
        myFixture.addFileToProject(
            "tests/test_dict.py", """
            class Test:
                class TestInner:
                    def test_(self):
                        pass
        """.trimIndent()
        )

        val contributor = PytestIdentifierContributor(project)
        val results = search(contributor, "tests/test_dict.py::Test::TestInner::test_")

        assertSize(1, results)
        val element = results[0].item
        assertTrue(element is PyFunction)
        assertEquals("test_", (element as PyFunction).name)
    }

    fun `test resolve partial node id`() {
        myFixture.addFileToProject("tests/__init__.py", "")
        myFixture.addFileToProject(
            "tests/test_partial_node.py", """
            def test_func():
                pass
        """.trimIndent()
        )

        val contributor = PytestIdentifierContributor(project)

        // Case 1: filename without extension
        val results1 = search(contributor, "test_partial_node::test_func")
        assertSize(1, results1)
        assertEquals("test_func", (results1[0].item as PyFunction).name)

        // Case 2: filename with extension but no path
        val results2 = search(contributor, "test_partial_node.py::test_func")
        assertSize(1, results2)
        assertEquals("test_func", (results2[0].item as PyFunction).name)

        // Case 3: partial filename matching
        val results3 = search(contributor, "partial_node::func")
        assertSize(1, results3)
        assertEquals("test_func", (results3[0].item as PyFunction).name)

        // Case 4: skipped class name
        myFixture.addFileToProject(
            "tests/test_skip.py", """
            class TestClass:
                def test_skipped_class(self):
                    pass
        """.trimIndent()
        )
        val results4 = search(contributor, "test_skip::test_skipped_class")
        assertSize(1, results4)
        assertEquals("test_skipped_class", (results4[0].item as PyFunction).name)
    }

    private fun search(
        contributor: WeightedSearchEverywhereContributor<PsiElement>,
        pattern: String
    ): List<FoundItemDescriptor<PsiElement>> {
        val results = ArrayList<FoundItemDescriptor<PsiElement>>()

        val processor = Processor<FoundItemDescriptor<PsiElement>> {
            results.add(it)
            true
        }

        contributor.fetchWeightedElements(pattern, EmptyProgressIndicator(), processor)

        return results
    }
}
