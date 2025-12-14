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
}
