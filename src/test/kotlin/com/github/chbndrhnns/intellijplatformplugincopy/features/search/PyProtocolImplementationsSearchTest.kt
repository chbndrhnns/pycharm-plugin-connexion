package com.github.chbndrhnns.intellijplatformplugincopy.features.search

import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.TypeEvalContext
import fixtures.TestBase

class PyProtocolImplementationsSearchTest : TestBase() {

    fun testSimpleProtocolImplementation() {
        // Define Protocol locally to avoid import resolution issues in test environment
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None:
                    pass
            
            class Square:
                def draw(self) -> None:
                    pass
            
            class NotDrawable:
                def paint(self) -> None:
                    pass
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)

        assertEquals(2, implementations.size)
        assertTrue(implementations.any { it.name == "Circle" })
        assertTrue(implementations.any { it.name == "Square" })
        assertFalse(implementations.any { it.name == "NotDrawable" })
    }

    fun testGoToImplementationForProtocol() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None:
                    pass
        """.trimIndent()
        )

        val gotoData =
            com.intellij.testFramework.fixtures.CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)

        assertTrue(gotoData.targets.isNotEmpty())
        assertTrue(gotoData.targets.any {
            it is PyClass && it.name == "Circle"
        })
    }

    fun testGoToImplementationForProtocolMethod() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Drawable(Protocol):
                def dr<caret>aw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None:
                    pass
            
            class Square:
                def draw(self) -> None:
                    pass
        """.trimIndent()
        )

        val gotoData =
            com.intellij.testFramework.fixtures.CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)

        assertTrue("Should find implementations for Protocol method", gotoData.targets.isNotEmpty())
        assertTrue("Should find Circle.draw", gotoData.targets.any {
            it is PyFunction && it.name == "draw" && it.containingClass?.name == "Circle"
        })
        assertTrue("Should find Square.draw", gotoData.targets.any {
            it is PyFunction && it.name == "draw" && it.containingClass?.name == "Square"
        })
    }

    fun testGoToImplementationForProtocolProperty() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class HasName(Protocol):
                na<caret>me: str
            
            class Person:
                name: str
                
            class Company:
                name: str
        """.trimIndent()
        )

        val gotoData =
            com.intellij.testFramework.fixtures.CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)

        assertTrue("Should find implementations for Protocol property", gotoData.targets.isNotEmpty())
        assertEquals("Should find 2 implementations", 2, gotoData.targets.size)
    }

    fun testProtocolMethodReturnTypeMismatch() {
        // Class with wrong return type should NOT be considered an implementation
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Get<caret>ter(Protocol):
                def get_value(self) -> int: ...
            
            class CorrectImpl:
                def get_value(self) -> int:
                    return 42
            
            class WrongReturnType:
                def get_value(self) -> str:
                    return "wrong"
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)

        assertTrue("Should find CorrectImpl", implementations.any { it.name == "CorrectImpl" })
        assertFalse(
            "Should NOT find WrongReturnType due to return type mismatch",
            implementations.any { it.name == "WrongReturnType" })
    }

    fun testProtocolMethodParameterTypeMismatch() {
        // Class with wrong parameter type should NOT be considered an implementation
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Pro<caret>cessor(Protocol):
                def process(self, value: int) -> None: ...
            
            class CorrectImpl:
                def process(self, value: int) -> None:
                    pass
            
            class WrongParamType:
                def process(self, value: str) -> None:
                    pass
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)

        assertTrue("Should find CorrectImpl", implementations.any { it.name == "CorrectImpl" })
        assertFalse(
            "Should NOT find WrongParamType due to parameter type mismatch",
            implementations.any { it.name == "WrongParamType" })
    }

    fun testProtocolAttributeTypeMismatch() {
        // Class with wrong attribute type should NOT be considered an implementation
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Has<caret>Value(Protocol):
                value: int
            
            class CorrectImpl:
                value: int
            
            class WrongAttrType:
                value: str
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)

        assertTrue("Should find CorrectImpl", implementations.any { it.name == "CorrectImpl" })
        assertFalse(
            "Should NOT find WrongAttrType due to attribute type mismatch",
            implementations.any { it.name == "WrongAttrType" })
    }

    fun testProtocolWithMultipleTypedMembers() {
        // Test protocol with multiple typed members - all must match
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Data<caret>Container(Protocol):
                name: str
                count: int
                def get_data(self) -> str: ...
            
            class FullyCorrect:
                name: str
                count: int
                def get_data(self) -> str:
                    return ""
            
            class WrongMethodReturn:
                name: str
                count: int
                def get_data(self) -> int:
                    return 0
            
            class WrongAttrType:
                name: int
                count: int
                def get_data(self) -> str:
                    return ""
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)

        assertTrue("Should find FullyCorrect", implementations.any { it.name == "FullyCorrect" })
        assertFalse(
            "Should NOT find WrongMethodReturn",
            implementations.any { it.name == "WrongMethodReturn" })
        assertFalse(
            "Should NOT find WrongAttrType",
            implementations.any { it.name == "WrongAttrType" })
    }

    fun testIsCallableOnlyProtocol() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Call<caret>able(Protocol):
                def __call__(self, x: int) -> str: ...
            
            class NotCallableOnly(Protocol):
                def __call__(self, x: int) -> str: ...
                name: str
        """.trimIndent()
        )

        val callableProtocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)

        assertTrue(
            "Callable protocol with only __call__ should be detected",
            PyProtocolImplementationsSearch.isCallableOnlyProtocol(callableProtocol, context)
        )

        // Find NotCallableOnly class
        val notCallableOnly = myFixture.file.children
            .filterIsInstance<PyClass>()
            .find { it.name == "NotCallableOnly" }!!

        assertFalse(
            "Protocol with __call__ and other members should NOT be callable-only",
            PyProtocolImplementationsSearch.isCallableOnlyProtocol(notCallableOnly, context)
        )
    }

    fun testLambdaMatchesCallableProtocol() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Call<caret>able(Protocol):
                def __call__(self, x: int) -> str: ...
            
            my_lambda = lambda x: str(x)
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)

        // Find the lambda expression
        val lambdaExpr = myFixture.file.children
            .flatMap { it.children.toList() }
            .filterIsInstance<PyLambdaExpression>()
            .firstOrNull()

        assertNotNull("Should find lambda expression", lambdaExpr)

        val lambdaType = context.getType(lambdaExpr!!) as? PyCallableType
        assertNotNull("Lambda should have a callable type", lambdaType)

        assertTrue(
            "Lambda should be compatible with callable protocol",
            PyProtocolImplementationsSearch.isCallableCompatibleWithCallProtocol(lambdaType!!, protocol, context)
        )
    }

    fun testLambdaWithWrongReturnTypeDoesNotMatchProtocol() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Call<caret>able(Protocol):
                def __call__(self, x: int) -> int: ...
            
            my_lambda = lambda x: "string_result"
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)

        // Find the lambda expression
        val lambdaExpr = myFixture.file.children
            .flatMap { it.children.toList() }
            .filterIsInstance<PyLambdaExpression>()
            .firstOrNull()

        assertNotNull("Should find lambda expression", lambdaExpr)

        val lambdaType = context.getType(lambdaExpr!!) as? PyCallableType
        assertNotNull("Lambda should have a callable type", lambdaType)

        assertFalse(
            "Lambda with wrong return type should NOT match protocol",
            PyProtocolImplementationsSearch.isCallableCompatibleWithCallProtocol(lambdaType!!, protocol, context)
        )
    }

    fun testFunctionMatchesCallableProtocol() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Call<caret>able(Protocol):
                def __call__(self, x: int) -> str: ...
            
            def my_func(x: int) -> str:
                return str(x)
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)

        // Find the function
        val func = myFixture.file.children
            .filterIsInstance<PyFunction>()
            .find { it.name == "my_func" }

        assertNotNull("Should find function", func)

        val funcType = context.getType(func!!) as? PyCallableType
        assertNotNull("Function should have a callable type", funcType)

        assertTrue(
            "Function with matching signature should match callable protocol",
            PyProtocolImplementationsSearch.isCallableCompatibleWithCallProtocol(funcType!!, protocol, context)
        )
    }

    fun testGetCallMethod() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Call<caret>able(Protocol):
                def __call__(self, x: int) -> str: ...
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass

        val callMethod = PyProtocolImplementationsSearch.getCallMethod(protocol)

        assertNotNull("Should find __call__ method", callMethod)
        assertEquals("__call__", callMethod!!.name)
    }

    fun testGoToImplementationForCallableProtocolFindsMatchingFunctions() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Call<caret>able(Protocol):
                def __call__(self, x: int) -> str: ...
            
            def matching_func(x: int) -> str:
                return str(x)
            
            def wrong_return_type(x: int) -> int:
                return x
        """.trimIndent()
        )

        val gotoData =
            com.intellij.testFramework.fixtures.CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)

        assertTrue("Should find implementations for callable protocol", gotoData.targets.isNotEmpty())
        assertTrue("Should find matching_func", gotoData.targets.any {
            it is PyFunction && it.name == "matching_func"
        })
        assertFalse("Should NOT find wrong_return_type due to return type mismatch", gotoData.targets.any {
            it is PyFunction && it.name == "wrong_return_type"
        })
    }

    fun testExcludesTestClassesFromProtocolImplementations() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None:
                    pass
            
            class TestCircle:
                def draw(self) -> None:
                    pass
            
            class test_square:
                def draw(self) -> None:
                    pass
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)

        assertEquals("Should find only 1 implementation (Circle)", 1, implementations.size)
        assertTrue("Should find Circle", implementations.any { it.name == "Circle" })
        assertFalse("Should NOT find TestCircle (test class)", implementations.any { it.name == "TestCircle" })
        assertFalse("Should NOT find test_square (test class)", implementations.any { it.name == "test_square" })
    }

    fun testIsTestClassDetection() {
        myFixture.configureByText(
            "test.py", """
            class Regular<caret>Class:
                pass
            
            class TestSomething:
                pass
            
            class test_another:
                pass
            
            class MyTestHelper:
                pass
        """.trimIndent()
        )

        val regularClass = myFixture.elementAtCaret as PyClass
        val testSomething = myFixture.file.children.filterIsInstance<PyClass>().find { it.name == "TestSomething" }!!
        val testAnother = myFixture.file.children.filterIsInstance<PyClass>().find { it.name == "test_another" }!!
        val myTestHelper = myFixture.file.children.filterIsInstance<PyClass>().find { it.name == "MyTestHelper" }!!

        assertFalse(
            "RegularClass should NOT be detected as test class",
            PyProtocolImplementationsSearch.isTestClass(regularClass)
        )
        assertTrue(
            "TestSomething should be detected as test class",
            PyProtocolImplementationsSearch.isTestClass(testSomething)
        )
        assertTrue(
            "test_another should be detected as test class",
            PyProtocolImplementationsSearch.isTestClass(testAnother)
        )
        assertFalse(
            "MyTestHelper should NOT be detected as test class (Test not at start)",
            PyProtocolImplementationsSearch.isTestClass(myTestHelper)
        )
    }

    fun testIsTestFunctionDetection() {
        myFixture.configureByText(
            "test.py", """
            def regular_function():
                pass
            
            def test_something():
                pass
            
            def my_test_helper():
                pass
        """.trimIndent()
        )

        val regularFunction =
            myFixture.file.children.filterIsInstance<PyFunction>().find { it.name == "regular_function" }!!
        val testSomething =
            myFixture.file.children.filterIsInstance<PyFunction>().find { it.name == "test_something" }!!
        val myTestHelper = myFixture.file.children.filterIsInstance<PyFunction>().find { it.name == "my_test_helper" }!!

        assertFalse(
            "regular_function should NOT be detected as test function",
            PyProtocolImplementationsSearch.isTestFunction(regularFunction)
        )
        assertTrue(
            "test_something should be detected as test function",
            PyProtocolImplementationsSearch.isTestFunction(testSomething)
        )
        assertFalse(
            "my_test_helper should NOT be detected as test function (test_ not at start)",
            PyProtocolImplementationsSearch.isTestFunction(myTestHelper)
        )
    }

    fun testGoToImplementationExcludesTestFunctions() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Call<caret>able(Protocol):
                def __call__(self, x: int) -> str: ...
            
            def matching_func(x: int) -> str:
                return str(x)
            
            def test_matching_func(x: int) -> str:
                return str(x)
        """.trimIndent()
        )

        val gotoData =
            com.intellij.testFramework.fixtures.CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)

        assertTrue("Should find matching_func", gotoData.targets.any {
            it is PyFunction && it.name == "matching_func"
        })
        assertFalse("Should NOT find test_matching_func (test function)", gotoData.targets.any {
            it is PyFunction && it.name == "test_matching_func"
        })
    }

    fun testUntypedLambdaMatchesCallableProtocol() {
        // This test reproduces the user's issue: untyped lambda should match a typed protocol
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class My<caret>Proto(Protocol):
                def __call__(self, x: int, y: str) -> int: ...
            
            my_lambda = lambda x, y: 1
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)

        // Find the lambda expression
        val lambdaExpr = myFixture.file.children
            .flatMap { it.children.toList() }
            .filterIsInstance<PyLambdaExpression>()
            .firstOrNull()

        assertNotNull("Should find lambda expression", lambdaExpr)

        val lambdaType = context.getType(lambdaExpr!!) as? PyCallableType
        assertNotNull("Lambda should have a callable type", lambdaType)

        // Untyped lambda with correct parameter count should match the protocol
        assertTrue(
            "Untyped lambda with matching parameter count should be compatible with callable protocol",
            PyProtocolImplementationsSearch.isCallableCompatibleWithCallProtocol(lambdaType!!, protocol, context)
        )
    }

    fun testUntypedLambdaWithFewerParamsDoesNotMatch() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class My<caret>Proto(Protocol):
                def __call__(self, x: int, y: str) -> int: ...
            
            my_lambda = lambda x: 1
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)

        // Find the lambda expression
        val lambdaExpr = myFixture.file.children
            .flatMap { it.children.toList() }
            .filterIsInstance<PyLambdaExpression>()
            .firstOrNull()

        assertNotNull("Should find lambda expression", lambdaExpr)

        val lambdaType = context.getType(lambdaExpr!!) as? PyCallableType
        assertNotNull("Lambda should have a callable type", lambdaType)

        // Lambda with fewer parameters should NOT match
        assertFalse(
            "Lambda with fewer parameters should NOT match protocol",
            PyProtocolImplementationsSearch.isCallableCompatibleWithCallProtocol(lambdaType!!, protocol, context)
        )
    }

    fun testUntypedLambdaWithMoreParamsDoesNotMatch() {
        // This test reproduces the user's issue: lambda x, y: 1 should NOT match __call__(self, y: str) -> int
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class My<caret>Proto(Protocol):
                def __call__(self, y: str) -> int: ...
            
            my_lambda = lambda x, y: 1
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)

        // Find the lambda expression
        val lambdaExpr = myFixture.file.children
            .flatMap { it.children.toList() }
            .filterIsInstance<PyLambdaExpression>()
            .firstOrNull()

        assertNotNull("Should find lambda expression", lambdaExpr)

        val lambdaType = context.getType(lambdaExpr!!) as? PyCallableType
        assertNotNull("Lambda should have a callable type", lambdaType)

        // Lambda with MORE parameters than protocol expects should NOT match
        assertFalse(
            "Lambda with more parameters than protocol expects should NOT match",
            PyProtocolImplementationsSearch.isCallableCompatibleWithCallProtocol(lambdaType!!, protocol, context)
        )
    }

    fun testGoToImplementationForCallableProtocolFindsMatchingLambdas() {
        // Lambdas are only found when used in a context where the protocol type is expected
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class My<caret>Proto(Protocol):
                def __call__(self, x: int, y: str) -> int: ...
            
            def use_proto(p: MyProto) -> int:
                return p(1, "a")
            
            # This lambda is passed where MyProto is expected - should be found
            use_proto(lambda x, y: 1)
            
            # This lambda is just assigned to a variable without type annotation - should NOT be found
            untyped_lambda = lambda x, y: 1
        """.trimIndent()
        )

        val gotoData =
            com.intellij.testFramework.fixtures.CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)

        assertTrue("Should find lambda passed as argument where protocol is expected", gotoData.targets.any {
            it is PyLambdaExpression
        })
    }

    fun testGoToImplementationDoesNotFindUntypedLambdas() {
        // Lambdas assigned to untyped variables should NOT be found as protocol implementations
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class My<caret>Proto(Protocol):
                def __call__(self, x: int, y: str) -> int: ...
            
            # This lambda is just assigned without type annotation - should NOT be found
            untyped_lambda = lambda x, y: 1
        """.trimIndent()
        )

        val gotoData =
            com.intellij.testFramework.fixtures.CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)

        assertFalse("Should NOT find untyped lambda without protocol context", gotoData.targets.any {
            it is PyLambdaExpression
        })
    }

    fun testGoToImplementationFindsUntypedLambdaWithMatchingParamCount() {
        // According to Python typing semantics, untyped lambda parameters are treated as Any,
        // which is compatible with any type. So lambda x: 1 SHOULD match __call__(self, y: list[str]) -> int
        // when passed in a context where the protocol is expected.
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class My<caret>Proto(Protocol):
                def __call__(self, y: list[str]) -> int: ...
            
            def main(do: MyProto):
                return do(["abc"])
            
            # This lambda has 1 param matching protocol's 1 param - should be found
            # (untyped params are treated as Any which is compatible with list[str])
            main(lambda x: 1)
        """.trimIndent()
        )

        val gotoData =
            com.intellij.testFramework.fixtures.CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)

        // The lambda SHOULD be found because param count matches and untyped params are Any
        assertTrue("Should find lambda with matching param count in protocol context", gotoData.targets.any {
            it is PyLambdaExpression
        })
    }
}
