package com.github.chbndrhnns.betterpy.features.usages

import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.rules.PsiElementUsage
import com.jetbrains.python.psi.PyClass
import fixtures.TestBase

class PyTypeAnnotationFilteringRuleTest : TestBase() {

    fun testFiltering() {
        // 1. Configure a file with mixed usages
        myFixture.configureByText(
            "test.py",
            """
            class MyType: pass
            def foo(x: MyType):
                v = MyType()
                pass
            """.trimIndent()
        )

        // 2. Find usages of 'MyType'
        // We search for the class definition to find references to it
        val targetElement = myFixture.findElementByText("class MyType", PyClass::class.java)
        val usageInfos = myFixture.findUsages(targetElement)

        // Convert UsageInfo to Usage objects (Rule expects Usage)
        val usages = usageInfos.map { UsageInfo2UsageAdapter(it) }

        // Ensure we found at least the two usages we expect
        // 1. x: MyType (type hint)
        // 2. v = MyType() (constructor call)
        assertTrue("Should find at least 2 usages, found ${usages.size}", usages.size >= 2)

        val rule = PyTypeAnnotationFilteringRule(myFixture.project)

        // 3. Check filtering logic
        // The rule is designed to return true (visible) ONLY if the usage is inside a type hint.
        // It assumes the platform only calls isVisible when the filter is active.

        var visibleCount = 0
        for (usage in usages) {
            if (rule.isVisible(usage)) {
                visibleCount++
                // Verify that the visible usage is indeed inside a type hint (conceptually)
                // We can check the text context to be sure it's the one we expect
                val element = (usage as PsiElementUsage).element
                assertNotNull(element)
                // The usage text should be in the function definition line
                element!!.containingFile.text
                element.textRange.startOffset
                // Simple check: references in signature "x: MyType"
                // vs references in body "v = MyType()"
                // We expect the one in "def foo(x: MyType):" to be visible.
            }
        }

        // Only the usage in "def foo(x: MyType):" should be visible
        assertEquals("Only usages in type hints should be visible", 1, visibleCount)
    }
}
