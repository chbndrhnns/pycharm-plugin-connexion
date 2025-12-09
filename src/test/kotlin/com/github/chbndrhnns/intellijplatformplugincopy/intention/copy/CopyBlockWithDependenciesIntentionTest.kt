package com.github.chbndrhnns.intellijplatformplugincopy.intention.copy

import com.intellij.openapi.ide.CopyPasteManager
import fixtures.TestBase
import java.awt.datatransfer.DataFlavor

class CopyBlockWithDependenciesIntentionTest : TestBase() {

    fun testSimpleFunction() {
        doCopyTest(
            """
      def <caret>foo():
          print("Hello")
      """,
            """
      def foo():
          print("Hello")
      """
        )
    }

    fun testFunctionWithImport() {
        doCopyTest(
            """
      import os
      
      def <caret>foo():
          print(os.getcwd())
      """,
            """
      import os
      
      def foo():
          print(os.getcwd())
      """
        )
    }

    fun testFunctionWithHelper() {
        doCopyTest(
            """
      def helper():
          return "Help"
          
      def <caret>foo():
          print(helper())
      """,
            """
      def helper():
          return "Help"
          
      def foo():
          print(helper())
      """
        )
    }

    fun testTransitiveDependency() {
        doCopyTest(
            """
      import math
      
      def sub_helper():
          return math.pi
          
      def helper():
          return sub_helper()
          
      def <caret>foo():
          print(helper())
      """,
            """
      import math

      def sub_helper():
          return math.pi
          
      def helper():
          return sub_helper()
          
      def foo():
          print(helper())
      """
        )
    }

    fun testIgnoresUnusedImportsAndHelpers() {
        doCopyTest(
            """
      import sys
      import os
      
      def unused_helper():
          pass
          
      def <caret>foo():
          print(os.name)
      """,
            """
      import os
      
      def foo():
          print(os.name)
      """
        )
    }

    fun testGlobalVariableDependency() {
        doCopyTest(
            """
            TIMEOUT = 100
            
            def <caret>foo():
                print(TIMEOUT)
            """,
            """
            TIMEOUT = 100
            
            def foo():
                print(TIMEOUT)
            """
        )
    }

    fun testLocalClassUsage() {
        doCopyTest(
            """
            class Config:
                pass
                
            def <caret>foo():
                c = Config()
                return c
            """,
            """
            class Config:
                pass
                
            def foo():
                c = Config()
                return c
            """
        )
    }

    fun testInheritance() {
        doCopyTest(
            """
            class Base:
                pass
                
            class Derived(Base):
                pass
                
            def <caret>foo():
                return Derived()
            """,
            """
            class Base:
                pass
                
            class Derived(Base):
                pass
                
            def foo():
                return Derived()
            """
        )
    }

    fun testAliasedImport() {
        doCopyTest(
            """
            import datetime as dt
            
            def <caret>foo():
                return dt.datetime.now()
            """,
            """
            import datetime as dt
            
            def foo():
                return dt.datetime.now()
            """
        )
    }

    fun testDecorator() {
        doCopyTest(
            """
            def my_decorator(f):
                return f
                
            @my_decorator
            def <caret>foo():
                pass
            """,
            """
            def my_decorator(f):
                return f
                
            @my_decorator
            def foo():
                pass
            """
        )
    }

    fun testTypeHints() {
        doCopyTest(
            """
            class User:
                pass
                
            def <caret>foo(u: User) -> None:
                pass
            """,
            """
            class User:
                pass
                
            def foo(u: User) -> None:
                pass
            """
        )
    }

    fun testDefaultArgument() {
        doCopyTest(
            """
            DEFAULT = 42
            
            def <caret>foo(x=DEFAULT):
                return x
            """,
            """
            DEFAULT = 42
            
            def foo(x=DEFAULT):
                return x
            """
        )
    }

    fun testMutualRecursion() {
        doCopyTest(
            """
            def bar():
                return foo()
                
            def <caret>foo():
                return bar()
            """,
            """
            def bar():
                return foo()
                
            def foo():
                return bar()
            """
        )
    }

    fun testShadowing() {
        doCopyTest(
            """
            x = 100
            
            def <caret>foo():
                x = 10
                print(x)
            """,
            """
            def foo():
                x = 10
                print(x)
            """
        )
    }

    fun testClassMethodCopiesClass() {
        doCopyTest(
            """
            class MyClass:
                def other(self):
                    pass
                    
                def <caret>target(self):
                    self.other()
            """,
            """
            class MyClass:
                def other(self):
                    pass
                    
                def target(self):
                    self.other()
            """
        )
    }

    fun testMultipleImportsOnOneLine() {
        doCopyTest(
            """
            import os, sys
            
            def <caret>foo():
                print(os.name)
            """,
            """
            import os, sys
            
            def foo():
                print(os.name)
            """
        )
    }

    fun testSimpleClass() {
        doCopyTest(
            """
            class <caret>MyClass:
                def method(self):
                    print("Hello")
            """,
            """
            class MyClass:
                def method(self):
                    print("Hello")
            """
        )
    }

    fun testClassWithDependency() {
        doCopyTest(
            """
            def helper():
                return "Help"

            class <caret>MyClass:
                def method(self):
                    print(helper())
            """,
            """
            def helper():
                return "Help"

            class MyClass:
                def method(self):
                    print(helper())
            """
        )
    }

    private fun doCopyTest(fileText: String, expectedClipboard: String) {
        myFixture.configureByText("a.py", fileText.trimIndent())

        // Simulate user invoking the intention
        val intentionName = "Copy element with dependencies" // Adjust to match actual name
        val intention = myFixture.availableIntentions.find { it.text == intentionName }
            ?: error("Intention '$intentionName' not found")

        myFixture.launchAction(intention)

        // Verify clipboard content
        val content = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor) as? String
        assertNotNull("Clipboard should not be empty", content)

        // Normalize newlines for comparison
        val expected = expectedClipboard.trimIndent().trim().lines().joinToString("\n") { it.trimEnd() }
        val actual = content!!.trim().lines().joinToString("\n") { it.trimEnd() }
        assertEquals(expected, actual)
    }
}
