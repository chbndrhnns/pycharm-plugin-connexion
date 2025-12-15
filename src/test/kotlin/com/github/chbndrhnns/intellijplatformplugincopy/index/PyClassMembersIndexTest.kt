package com.github.chbndrhnns.intellijplatformplugincopy.index

import com.github.chbndrhnns.intellijplatformplugincopy.search.PyProtocolImplementationsSearch
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.types.TypeEvalContext
import fixtures.TestBase

/**
 * Tests for [PyClassMembersFileIndex].
 * 
 * The file-based index maps member names (methods and attributes) to files containing
 * classes that define them. This enables efficient O(1) lookup of candidate files,
 * which are then resolved to find matching classes.
 * 
 * These tests verify that:
 * 1. The file-based index correctly indexes class members
 * 2. The index is used by PyProtocolImplementationsSearch to find implementations
 */
class PyClassMembersIndexTest : TestBase() {

    fun testFileIndexFindsFileWithMethod() {
        myFixture.configureByText(
            "test.py", """
            class MyClass:
                def my_method(self) -> None:
                    pass
        """.trimIndent()
        )

        // The file-based index should find the file containing the class with this method
        val files = PyClassMembersFileIndex.findFilesWithMember(
            "my_method", project, GlobalSearchScope.fileScope(myFixture.file)
        )

        assertEquals("Index should find the file containing the class with the method", 1, files.size)
        assertEquals(myFixture.file.virtualFile, files.first())
    }

    fun testFileIndexFindsFileWithAttribute() {
        myFixture.configureByText(
            "test.py", """
            class MyClass:
                my_attr: str
        """.trimIndent()
        )

        val files = PyClassMembersFileIndex.findFilesWithMember(
            "my_attr", project, GlobalSearchScope.fileScope(myFixture.file)
        )

        assertEquals("Index should find the file containing the class with the attribute", 1, files.size)
        assertEquals(myFixture.file.virtualFile, files.first())
    }

    fun testFileIndexDoesNotIndexPrivateMembers() {
        myFixture.configureByText(
            "test.py", """
            class MyClass:
                def _private_method(self) -> None:
                    pass
                _private_attr: str
        """.trimIndent()
        )

        val methodFiles = PyClassMembersFileIndex.findFilesWithMember(
            "_private_method", project, GlobalSearchScope.fileScope(myFixture.file)
        )
        val attrFiles = PyClassMembersFileIndex.findFilesWithMember(
            "_private_attr", project, GlobalSearchScope.fileScope(myFixture.file)
        )

        assertTrue("Index should not index private methods", methodFiles.isEmpty())
        assertTrue("Index should not index private attributes", attrFiles.isEmpty())
    }

    fun testSearchFindsClassWithMethod() {
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

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)

        assertEquals(1, implementations.size)
        assertEquals("Circle", implementations.first().name)
    }

    fun testSearchFindsMultipleImplementations() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None: pass
            
            class Square:
                def draw(self) -> None: pass
            
            class NotDrawable:
                def paint(self) -> None: pass
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

    fun testSearchFindsClassWithAttribute() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Has<caret>Name(Protocol):
                name: str
            
            class Person:
                name: str
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)

        assertEquals(1, implementations.size)
        assertEquals("Person", implementations.first().name)
    }
}
