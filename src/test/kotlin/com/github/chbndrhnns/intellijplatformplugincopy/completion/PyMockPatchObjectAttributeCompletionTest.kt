package com.github.chbndrhnns.intellijplatformplugincopy.completion

import fixtures.TestBase

class PyMockPatchObjectAttributeCompletionTest : TestBase() {

    fun testCompletionSuggestsClassMethods() {
        myFixture.configureByText(
            "test_completion.py", """
            from unittest.mock import patch
            
            class MyService:
                def fetch_data(self): pass
                def process(self): pass
                
            patch.object(MyService, "<caret>")
        """.trimIndent()
        )

        val lookupElements = myFixture.completeBasic()
        val lookupStrings = lookupElements?.map { it.lookupString } ?: emptyList()

        assertTrue("Should suggest 'fetch_data'", lookupStrings.contains("fetch_data"))
        assertTrue("Should suggest 'process'", lookupStrings.contains("process"))
    }

    fun testCompletionSuggestsClassAttributes() {
        myFixture.configureByText(
            "test_completion_attr.py", """
            from unittest.mock import patch
            
            class MyService:
                name = "service"
                count = 0
                
            patch.object(MyService, "<caret>")
        """.trimIndent()
        )

        val lookupElements = myFixture.completeBasic()
        val lookupStrings = lookupElements?.map { it.lookupString } ?: emptyList()

        assertTrue("Should suggest 'name'", lookupStrings.contains("name"))
        assertTrue("Should suggest 'count'", lookupStrings.contains("count"))
    }
}
