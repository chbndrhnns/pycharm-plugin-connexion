package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import fixtures.TestBase

class PyMockPatchFalsePositiveTest : TestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(
            PyMockPatchUnresolvedReferenceInspection(),
            PyMockPatchObjectAttributeInspection()
        )
    }

    fun testNoInspectionOnCustomPatch() {
        myFixture.configureByText(
            "test_custom.py", """
            class Custom:
                def patch(self, target):
                    pass
            
            c = Custom()
            c.patch('non.existent.path')
        """.trimIndent()
        )
        myFixture.checkHighlighting()
    }

    fun testNoInspectionOnCustomPatchObject() {
        myFixture.configureByText(
            "test_custom_object.py", """
            class Custom:
                def object(self, target, attribute):
                    pass
            
            class Patch:
                def __init__(self):
                    self.object = Custom().object
                
            patch = Patch()
            patch.object(None, 'non_existent_attribute')
        """.trimIndent()
        )
        myFixture.checkHighlighting()
    }

    fun testNoInspectionOnGlobalPatch() {
        myFixture.configureByText(
            "test_global.py", """
            def patch(target):
                pass
            
            patch('non.existent.path')
        """.trimIndent()
        )
        myFixture.checkHighlighting()
    }
}
