package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class ParameterObjectLineMarkerProviderTest : TestBase() {

    fun testGutterIconAvailableForFunctionWithParameters() {
        myFixture.configureByText(
            "a.py", """
            def my_function(name: str, age: int, email: str):
                pass
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!

        val provider = ParameterObjectLineMarkerProvider()
        val info = provider.getLineMarkerInfo(function)

        assertNotNull("Gutter icon should be available for function with parameters", info)
    }

    fun testGutterIconNotAvailableForFunctionWithNoParameters() {
        myFixture.configureByText(
            "b.py", """
            def my_function():
                pass
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!

        val provider = ParameterObjectLineMarkerProvider()
        val info = provider.getLineMarkerInfo(function)

        assertNull("Gutter icon should NOT be available for function with no parameters", info)
    }

    fun testGutterIconNotAvailableForFunctionWithOnlySelf() {
        myFixture.configureByText(
            "c.py", """
            class MyClass:
                def my_method(self):
                    pass
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!

        val provider = ParameterObjectLineMarkerProvider()
        val info = provider.getLineMarkerInfo(function)

        assertNull("Gutter icon should NOT be available for method with only self", info)
    }

    fun testGutterIconAvailableForMethodWithParameters() {
        myFixture.configureByText(
            "d.py", """
            class MyClass:
                def my_method(self, name: str, age: int):
                    pass
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!

        val provider = ParameterObjectLineMarkerProvider()
        val info = provider.getLineMarkerInfo(function)

        assertNotNull("Gutter icon should be available for method with parameters", info)
    }

    fun testGutterIconNotAvailableWhenGutterIconDisabled() {
        PluginSettingsState.instance().state.parameterObject.enableParameterObjectGutterIcon = false

        myFixture.configureByText(
            "e.py", """
            def my_function(name: str, age: int, email: str):
                pass
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!

        val provider = ParameterObjectLineMarkerProvider()
        val info = provider.getLineMarkerInfo(function)

        assertNull("Gutter icon should NOT be available when gutter icon setting is disabled", info)
    }

    fun testGutterIconNotAvailableWhenRefactoringDisabled() {
        PluginSettingsState.instance().state.parameterObject.enableParameterObjectRefactoring = false

        myFixture.configureByText(
            "f.py", """
            def my_function(name: str, age: int, email: str):
                pass
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!

        val provider = ParameterObjectLineMarkerProvider()
        val info = provider.getLineMarkerInfo(function)

        assertNull("Gutter icon should NOT be available when refactoring is disabled", info)
    }

    fun testGutterIconNotAvailableForTestFunction() {
        myFixture.configureByText(
            "test_module.py", """
            def test_something(name: str, age: int, email: str):
                pass
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!

        val provider = ParameterObjectLineMarkerProvider()
        val info = provider.getLineMarkerInfo(function)

        assertNull("Gutter icon should NOT be available for test functions", info)
    }

    fun testGutterIconNotAvailableForPytestFixture() {
        myFixture.configureByText(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture(name: str, age: int):
                return name
        """.trimIndent()
        )

        val function = PsiTreeUtil.findChildOfType(myFixture.file, PyFunction::class.java)!!

        val provider = ParameterObjectLineMarkerProvider()
        val info = provider.getLineMarkerInfo(function)

        assertNull("Gutter icon should NOT be available for pytest fixtures", info)
    }

    fun testGutterIconNotAvailableWhenParameterObjectAlreadyExists_Dataclass() {
        myFixture.configureByText(
            "g.py", """
            from dataclasses import dataclass
            
            @dataclass
            class UserData:
                name: str
                age: int
                email: str
            
            def process_user(user: UserData):
                pass
        """.trimIndent()
        )

        val functions = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java)
        val processUserFunction = functions.first { it.name == "process_user" }

        val provider = ParameterObjectLineMarkerProvider()
        val info = provider.getLineMarkerInfo(processUserFunction)

        assertNull("Gutter icon should NOT be available when function already has a dataclass parameter object", info)
    }

    fun testGutterIconNotAvailableWhenParameterObjectAlreadyExists_TypedDict() {
        myFixture.configureByText(
            "h.py", """
            from typing import TypedDict
            
            class UserData(TypedDict):
                name: str
                age: int
                email: str
            
            def process_user(user: UserData):
                pass
        """.trimIndent()
        )

        val functions = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java)
        val processUserFunction = functions.first { it.name == "process_user" }

        val provider = ParameterObjectLineMarkerProvider()
        val info = provider.getLineMarkerInfo(processUserFunction)

        assertNull("Gutter icon should NOT be available when function already has a TypedDict parameter object", info)
    }

    fun testGutterIconNotAvailableWhenParameterObjectAlreadyExists_NamedTuple() {
        myFixture.configureByText(
            "i.py", """
            from typing import NamedTuple
            
            class UserData(NamedTuple):
                name: str
                age: int
                email: str
            
            def process_user(user: UserData):
                pass
        """.trimIndent()
        )

        val functions = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java)
        val processUserFunction = functions.first { it.name == "process_user" }

        val provider = ParameterObjectLineMarkerProvider()
        val info = provider.getLineMarkerInfo(processUserFunction)

        assertNull("Gutter icon should NOT be available when function already has a NamedTuple parameter object", info)
    }

}
