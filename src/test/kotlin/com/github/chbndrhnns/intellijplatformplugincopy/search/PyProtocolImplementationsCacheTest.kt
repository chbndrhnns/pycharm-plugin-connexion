package com.github.chbndrhnns.intellijplatformplugincopy.search

import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.types.TypeEvalContext
import fixtures.TestBase

class PyProtocolImplementationsCacheTest : TestBase() {

    override fun setUp() {
        super.setUp()
        PyProtocolImplementationsSearch.invalidateCache()
    }

    override fun tearDown() {
        PyProtocolImplementationsSearch.invalidateCache()
        super.tearDown()
    }

    fun testCachedSearchReturnsSameResults() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None: pass
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        val result1 = PyProtocolImplementationsSearch.search(protocol, scope, context)
        val result2 = PyProtocolImplementationsSearch.search(protocol, scope, context)

        assertEquals(result1.size, result2.size)
        assertEquals(result1.map { it.name }.toSet(), result2.map { it.name }.toSet())
    }

    fun testCacheInvalidationClearsResults() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None: pass
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        // First search populates cache
        val result1 = PyProtocolImplementationsSearch.search(protocol, scope, context)
        assertEquals(1, result1.size)

        // Invalidate cache
        PyProtocolImplementationsSearch.invalidateCache()

        // Second search should still work after invalidation
        val result2 = PyProtocolImplementationsSearch.search(protocol, scope, context)
        assertEquals(1, result2.size)
    }

    fun testInvalidateCacheForSpecificProtocol() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None: pass
        """.trimIndent()
        )

        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        // First search populates cache
        PyProtocolImplementationsSearch.search(protocol, scope, context)

        // Invalidate cache for specific protocol
        val qName = protocol.qualifiedName
        if (qName != null) {
            PyProtocolImplementationsSearch.invalidateCacheFor(qName)
        }

        // Search should still work after specific invalidation
        val result = PyProtocolImplementationsSearch.search(protocol, scope, context)
        assertEquals(1, result.size)
        assertEquals("Circle", result.first().name)
    }

    fun testSearchWithMultipleProtocols() {
        myFixture.configureByText(
            "test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Printable(Protocol):
                def print_out(self) -> None: ...
            
            class Circle:
                def draw(self) -> None: pass
            
            class Document:
                def print_out(self) -> None: pass
        """.trimIndent()
        )

        val drawable = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)

        // Search for Drawable implementations
        val drawableImpls = PyProtocolImplementationsSearch.search(drawable, scope, context)
        assertEquals(1, drawableImpls.size)
        assertEquals("Circle", drawableImpls.first().name)

        // Find Printable protocol
        val printable = myFixture.file.children
            .filterIsInstance<PyClass>()
            .find { it.name == "Printable" }!!

        // Search for Printable implementations
        val printableImpls = PyProtocolImplementationsSearch.search(printable, scope, context)
        assertEquals(1, printableImpls.size)
        assertEquals("Document", printableImpls.first().name)
    }
}
