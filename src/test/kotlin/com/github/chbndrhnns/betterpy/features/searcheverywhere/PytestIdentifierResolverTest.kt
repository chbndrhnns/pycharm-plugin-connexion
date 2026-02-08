package com.github.chbndrhnns.betterpy.features.searcheverywhere

import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PytestIdentifierResolverTest : TestBase() {

    fun testResolveSupportsPytestSugarDotSeparatedClassAndTest() {
        myFixture.addFileToProject(
            "tests/unit/adapters/inbound/web/network_policies/test_get_api.py",
            """
                class TestGetAll:
                    def test_returns_all(self):
                        pass
            """.trimIndent(),
        )

        val resolver = PytestIdentifierResolver(project)
        val element = resolver.resolve(
            "tests/unit/adapters/inbound/web/network_policies/test_get_api.py::TestGetAll.test_returns_all",
        )

        assertNotNull(element)
        assertTrue(element is PyFunction)
        assertEquals("test_returns_all", (element as PyFunction).name)
    }

    fun testResolveSupportsPytestSugarNestedDotSeparatedContainers() {
        myFixture.addFileToProject(
            "tests/unit/test_nested.py",
            """
                class TestOuter:
                    class TestInner:
                        def test_x(self):
                            pass
            """.trimIndent(),
        )

        val resolver = PytestIdentifierResolver(project)
        val element = resolver.resolve("tests/unit/test_nested.py::TestOuter.TestInner.test_x")

        assertNotNull(element)
        assertTrue(element is PyFunction)
        val function = element as PyFunction
        assertEquals("test_x", function.name)

        val containingClass = function.containingClass
        assertNotNull(containingClass)
        assertTrue(containingClass is PyClass)
        assertEquals("TestInner", containingClass!!.name)
    }
}
