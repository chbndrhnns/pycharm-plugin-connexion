package com.github.chbndrhnns.intellijplatformplugincopy.search

import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext
import fixtures.TestBase

class PyProtocolImplementationsSearchTest : TestBase() {
    
    fun testSimpleProtocolImplementation() {
        // Define Protocol locally to avoid import resolution issues in test environment
        myFixture.configureByText("test.py", """
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
        """.trimIndent())
        
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
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None:
                    pass
        """.trimIndent())
        
        val gotoData = com.intellij.testFramework.fixtures.CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)
        
        assertTrue(gotoData.targets.isNotEmpty())
        assertTrue(gotoData.targets.any { 
            it is PyClass && it.name == "Circle" 
        })
    }
    
    fun testGoToImplementationForProtocolMethod() {
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class Drawable(Protocol):
                def dr<caret>aw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None:
                    pass
            
            class Square:
                def draw(self) -> None:
                    pass
        """.trimIndent())
        
        val gotoData = com.intellij.testFramework.fixtures.CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)
        
        assertTrue("Should find implementations for Protocol method", gotoData.targets.isNotEmpty())
        assertTrue("Should find Circle.draw", gotoData.targets.any { 
            it is PyFunction && it.name == "draw" && it.containingClass?.name == "Circle" 
        })
        assertTrue("Should find Square.draw", gotoData.targets.any { 
            it is PyFunction && it.name == "draw" && it.containingClass?.name == "Square" 
        })
    }
    
    fun testGoToImplementationForProtocolProperty() {
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class HasName(Protocol):
                na<caret>me: str
            
            class Person:
                name: str
                
            class Company:
                name: str
        """.trimIndent())
        
        val gotoData = com.intellij.testFramework.fixtures.CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)
        
        assertTrue("Should find implementations for Protocol property", gotoData.targets.isNotEmpty())
        assertEquals("Should find 2 implementations", 2, gotoData.targets.size)
    }

    fun testProtocolMethodReturnTypeMismatch() {
        // Class with wrong return type should NOT be considered an implementation
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class Get<caret>ter(Protocol):
                def get_value(self) -> int: ...
            
            class CorrectImpl:
                def get_value(self) -> int:
                    return 42
            
            class WrongReturnType:
                def get_value(self) -> str:
                    return "wrong"
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)
        
        assertTrue("Should find CorrectImpl", implementations.any { it.name == "CorrectImpl" })
        assertFalse("Should NOT find WrongReturnType due to return type mismatch", 
            implementations.any { it.name == "WrongReturnType" })
    }

    fun testProtocolMethodParameterTypeMismatch() {
        // Class with wrong parameter type should NOT be considered an implementation
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class Pro<caret>cessor(Protocol):
                def process(self, value: int) -> None: ...
            
            class CorrectImpl:
                def process(self, value: int) -> None:
                    pass
            
            class WrongParamType:
                def process(self, value: str) -> None:
                    pass
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)
        
        assertTrue("Should find CorrectImpl", implementations.any { it.name == "CorrectImpl" })
        assertFalse("Should NOT find WrongParamType due to parameter type mismatch", 
            implementations.any { it.name == "WrongParamType" })
    }

    fun testProtocolAttributeTypeMismatch() {
        // Class with wrong attribute type should NOT be considered an implementation
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class Has<caret>Value(Protocol):
                value: int
            
            class CorrectImpl:
                value: int
            
            class WrongAttrType:
                value: str
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)
        
        assertTrue("Should find CorrectImpl", implementations.any { it.name == "CorrectImpl" })
        assertFalse("Should NOT find WrongAttrType due to attribute type mismatch", 
            implementations.any { it.name == "WrongAttrType" })
    }

    fun testProtocolWithMultipleTypedMembers() {
        // Test protocol with multiple typed members - all must match
        myFixture.configureByText("test.py", """
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
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)
        
        assertTrue("Should find FullyCorrect", implementations.any { it.name == "FullyCorrect" })
        assertFalse("Should NOT find WrongMethodReturn", 
            implementations.any { it.name == "WrongMethodReturn" })
        assertFalse("Should NOT find WrongAttrType", 
            implementations.any { it.name == "WrongAttrType" })
    }
}
