### How to Show Python File Members in the Navigation Bar (Footer Bar)

The navigation bar (footer bar) in PyCharm currently shows the file path but doesn't show members (functions, classes, methods) inside Python files, unlike Java/Kotlin. This is because Python is missing a **NavBarModelExtension** implementation. Here's how to implement it as a plugin.

---

### Architecture Overview

The navigation bar uses the **NavBarModelExtension** extension point to determine what elements to show. For languages that want to display members (methods, functions, classes), they need to:

1. Implement `StructureAwareNavBarModelExtension` - which uses the language's structure view
2. Register it via the `com.intellij.navbar` extension point
3. The extension automatically shows members when `UISettings.showMembersInNavigationBar` is enabled

---

### Implementation

#### Step 1: Create PyNavBarModelExtension

Create a new file: `python/src/com/jetbrains/python/navbar/PyNavBarModelExtension.kt`

```kotlin
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.navbar

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression

/**
 * Navigation bar extension for Python that shows file members (classes, functions, methods)
 * in the navigation bar when "Show Members in Navigation Bar" is enabled.
 */
class PyNavBarModelExtension : StructureAwareNavBarModelExtension() {
    
    override val language: Language
        get() = PythonLanguage.getInstance()

    override fun getPresentableText(item: Any?): String? {
        return when (item) {
            is PyFunction -> {
                val name = item.name ?: return "function"
                if (item.isAsync) "async $name()" else "$name()"
            }
            is PyClass -> item.name ?: "class"
            is PyTargetExpression -> item.name
            else -> null
        }
    }

    override fun adjustElement(psiElement: PsiElement): PsiElement {
        // Return the element as-is for Python
        // Could be enhanced to handle special cases like single-class files
        return psiElement
    }

    override fun acceptParentFromModel(psiElement: PsiElement?): Boolean {
        // Accept all parents from the structure view model
        return true
    }
}
```

#### Step 2: Register the Extension

Add to `python/ide/resources/META-INF/plugin.xml` (or create a separate XML file like `navigation.xml`):

```xml
<idea-plugin>
  <extensions defaultExtensionNs="com.intellij">
    <navbar implementation="com.jetbrains.python.navbar.PyNavBarModelExtension"/>
  </extensions>
</idea-plugin>
```

If creating a separate file, include it in the main plugin.xml:

```xml
<xi:include href="/META-INF/navigation.xml" xpointer="xpointer(/idea-plugin/*)"/>
```

---

### Testing

#### Step 3: Create Test Class

Create: `python/testSrc/com/jetbrains/python/navbar/PyNavBarModelExtensionTest.kt`

```kotlin
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.navbar

import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.ide.ui.UISettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction

class PyNavBarModelExtensionTest : BasePlatformTestCase() {

    private lateinit var extension: PyNavBarModelExtension
    private var originalShowMembers: Boolean = false

    override fun setUp() {
        super.setUp()
        extension = NavBarModelExtension.EP_NAME.findExtension(PyNavBarModelExtension::class.java)
            ?: throw AssertionError("PyNavBarModelExtension not registered")
        originalShowMembers = UISettings.getInstance().showMembersInNavigationBar
        UISettings.getInstance().showMembersInNavigationBar = true
    }

    override fun tearDown() {
        try {
            UISettings.getInstance().showMembersInNavigationBar = originalShowMembers
        } finally {
            super.tearDown()
        }
    }

    fun testShowFunctionInNavigationBar() {
        val file = myFixture.configureByText("test.py", """
            def foo():
                <caret>pass
        """.trimIndent())

        val element = file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)
        
        val function = extension.getParent(element!!)
        assertInstanceOf(function, PyFunction::class.java)
        assertEquals("foo()", extension.getPresentableText(function))
    }

    fun testShowMethodInClass() {
        val file = myFixture.configureByText("test.py", """
            class MyClass:
                def my_method(self):
                    <caret>pass
        """.trimIndent())

        val element = file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)
        
        // Should show both class and method
        val method = extension.getParent(element!!)
        assertInstanceOf(method, PyFunction::class.java)
        assertEquals("my_method()", extension.getPresentableText(method))
        
        val cls = extension.getParent(method!!)
        assertInstanceOf(cls, PyClass::class.java)
        assertEquals("MyClass", extension.getPresentableText(cls))
    }

    fun testShowAsyncFunction() {
        val file = myFixture.configureByText("test.py", """
            async def async_foo():
                <caret>pass
        """.trimIndent())

        val element = file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)
        
        val function = extension.getParent(element!!)
        assertInstanceOf(function, PyFunction::class.java)
        assertEquals("async async_foo()", extension.getPresentableText(function))
    }

    fun testShowNestedFunction() {
        val file = myFixture.configureByText("test.py", """
            def outer():
                def inner():
                    <caret>pass
        """.trimIndent())

        val element = file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)
        
        // Should show nested structure
        val inner = extension.getParent(element!!)
        assertInstanceOf(inner, PyFunction::class.java)
        assertEquals("inner()", extension.getPresentableText(inner))
        
        val outer = extension.getParent(inner!!)
        assertInstanceOf(outer, PyFunction::class.java)
        assertEquals("outer()", extension.getPresentableText(outer))
    }

    fun testShowClassOnly() {
        val file = myFixture.configureByText("test.py", """
            class MyClass:
                <caret>pass
        """.trimIndent())

        val element = file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)
        
        val cls = extension.getParent(element!!)
        assertInstanceOf(cls, PyClass::class.java)
        assertEquals("MyClass", extension.getPresentableText(cls))
    }

    fun testDisabledWhenShowMembersOff() {
        UISettings.getInstance().showMembersInNavigationBar = false
        
        val file = myFixture.configureByText("test.py", """
            def foo():
                <caret>pass
        """.trimIndent())

        val element = file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)
        
        // When showMembersInNavigationBar is false, should not show function
        val parent = extension.getParent(element!!)
        // Should return file or null, not the function
        assertFalse(parent is PyFunction)
    }
}
```

#### Alternative: Integration Test with Navigation Bar UI

Create: `python/testSrc/com/jetbrains/python/navbar/PyNavBarIntegrationTest.kt`

```kotlin
package com.jetbrains.python.navbar

import com.intellij.ide.ui.UISettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PyNavBarIntegrationTest : BasePlatformTestCase() {

    private var originalShowMembers: Boolean = false

    override fun setUp() {
        super.setUp()
        originalShowMembers = UISettings.getInstance().showMembersInNavigationBar
        UISettings.getInstance().showMembersInNavigationBar = true
    }

    override fun tearDown() {
        try {
            UISettings.getInstance().showMembersInNavigationBar = originalShowMembers
        } finally {
            super.tearDown()
        }
    }

    fun testNavigationBarShowsMembers() {
        myFixture.configureByText("test.py", """
            class Calculator:
                def add(self, a, b):
                    return a + <caret>b
        """.trimIndent())

        // Get breadcrumbs at caret (navigation bar uses same model)
        val breadcrumbs = myFixture.getBreadcrumbsAtCaret()
        
        assertTrue("Should show class in breadcrumbs", 
                   breadcrumbs.any { it.text == "Calculator" })
        assertTrue("Should show method in breadcrumbs", 
                   breadcrumbs.any { it.text == "add()" })
    }
}
```

---

### How It Works

1. **Structure View Integration**: The `StructureAwareNavBarModelExtension` uses Python's existing `PyStructureViewModel` which already knows about classes, functions, and methods.

2. **Conditional Display**: Members are only shown when `UISettings.showMembersInNavigationBar` is `true`. Users can toggle this via:
   - **View → Appearance → Navigation Bar → Show Members**

3. **Breadcrumbs Relationship**: The Java implementation shows that breadcrumbs and navigation bar are related - when members are shown in the navigation bar, breadcrumbs are hidden by default (see `JavaBreadcrumbsInfoProvider.isShownByDefault()`).

4. **Element Hierarchy**: The extension uses `getParent()` to build the hierarchy: File → Class → Method → nested elements.

---

### Key Extension Points

- **`com.intellij.navbar`**: Extension point for navigation bar model extensions
- **`StructureAwareNavBarModelExtension`**: Base class that uses structure view
- **`PyStructureViewModel`**: Already exists and provides the structure information

---

### Testing the Plugin

1. **Build and run** the plugin in a test IDE instance
2. **Enable members**: Go to **View → Appearance → Navigation Bar → Show Members**
3. **Open a Python file** with classes and methods
4. **Observe the navigation bar** at the bottom - it should now show the current class/method
5. **Click on elements** in the navigation bar to navigate

---

### Additional Enhancements (Optional)

You can enhance the implementation with:

1. **Better formatting** for methods with parameters
2. **Support for decorators** (e.g., `@property`, `@staticmethod`)
3. **Lambda expressions** in the navigation bar
4. **Custom icons** for different element types
5. **Tooltips** with full signatures

Example enhancement:

```kotlin
override fun getPresentableText(item: Any?): String? {
    return when (item) {
        is PyFunction -> {
            val name = item.name ?: return "function"
            val prefix = when {
                item.isAsync -> "async "
                item.modifier == PyFunction.Modifier.STATICMETHOD -> "@staticmethod "
                item.modifier == PyFunction.Modifier.CLASSMETHOD -> "@classmethod "
                else -> ""
            }
            "$prefix$name()"
        }
        // ... rest of implementation
    }
}
```

---

### References

- **Java Implementation**: `com.intellij.ide.navigationToolbar.JavaNavBarExtension`
- **Kotlin Implementation**: `org.jetbrains.kotlin.idea.navigationToolbar.KotlinNavBarModelExtension`
- **Base Class**: `com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension`
- **Python Structure View**: `com.jetbrains.python.structureView.PyStructureViewModel`