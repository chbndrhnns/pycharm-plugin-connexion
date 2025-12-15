package com.github.chbndrhnns.intellijplatformplugincopy.index

import com.intellij.psi.search.GlobalSearchScope
import fixtures.TestBase

/**
 * Tests for [PyLambdaFileIndex].
 * 
 * The file-based index tracks files containing lambda expressions, enabling efficient
 * lookup of files with lambdas for protocol matching without iterating all Python files.
 */
class PyLambdaFileIndexTest : TestBase() {

    fun testFileIndexFindsFileWithLambda() {
        myFixture.configureByText(
            "test.py", """
            my_func = lambda x: x + 1
        """.trimIndent()
        )

        val files = PyLambdaFileIndex.findFilesWithLambdas(
            project, GlobalSearchScope.fileScope(myFixture.file)
        )

        assertEquals("Index should find the file containing a lambda", 1, files.size)
        assertEquals(myFixture.file.virtualFile, files.first())
    }

    fun testFileIndexFindsFileWithMultipleLambdas() {
        myFixture.configureByText(
            "test.py", """
            add = lambda x, y: x + y
            multiply = lambda x, y: x * y
            identity = lambda x: x
        """.trimIndent()
        )

        val files = PyLambdaFileIndex.findFilesWithLambdas(
            project, GlobalSearchScope.fileScope(myFixture.file)
        )

        assertEquals("Index should find the file containing lambdas", 1, files.size)
        assertEquals(myFixture.file.virtualFile, files.first())
    }

    fun testFileIndexDoesNotFindFileWithoutLambda() {
        myFixture.configureByText(
            "test.py", """
            def my_func(x):
                return x + 1
            
            class MyClass:
                def method(self):
                    pass
        """.trimIndent()
        )

        val files = PyLambdaFileIndex.findFilesWithLambdas(
            project, GlobalSearchScope.fileScope(myFixture.file)
        )

        assertTrue("Index should not find files without lambdas", files.isEmpty())
    }

    fun testFileIndexFindsLambdaInClassMethod() {
        myFixture.configureByText(
            "test.py", """
            class MyClass:
                def method(self):
                    return lambda x: x * 2
        """.trimIndent()
        )

        val files = PyLambdaFileIndex.findFilesWithLambdas(
            project, GlobalSearchScope.fileScope(myFixture.file)
        )

        assertEquals("Index should find file with lambda inside class method", 1, files.size)
        assertEquals(myFixture.file.virtualFile, files.first())
    }

    fun testFileIndexFindsLambdaAsArgument() {
        myFixture.configureByText(
            "test.py", """
            result = map(lambda x: x * 2, [1, 2, 3])
        """.trimIndent()
        )

        val files = PyLambdaFileIndex.findFilesWithLambdas(
            project, GlobalSearchScope.fileScope(myFixture.file)
        )

        assertEquals("Index should find file with lambda as function argument", 1, files.size)
        assertEquals(myFixture.file.virtualFile, files.first())
    }

    fun testFileIndexFindsNestedLambda() {
        myFixture.configureByText(
            "test.py", """
            nested = lambda x: lambda y: x + y
        """.trimIndent()
        )

        val files = PyLambdaFileIndex.findFilesWithLambdas(
            project, GlobalSearchScope.fileScope(myFixture.file)
        )

        assertEquals("Index should find file with nested lambdas", 1, files.size)
        assertEquals(myFixture.file.virtualFile, files.first())
    }
}
