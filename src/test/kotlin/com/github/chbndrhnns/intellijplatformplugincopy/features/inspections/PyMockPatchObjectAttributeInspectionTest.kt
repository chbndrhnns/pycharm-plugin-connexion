package com.github.chbndrhnns.intellijplatformplugincopy.features.inspections

import fixtures.TestBase

class PyMockPatchObjectAttributeInspectionTest : TestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(PyMockPatchObjectAttributeInspection::class.java)
    }

    fun testUnresolvedAttributeOnPatchObject() {
        myFixture.configureByText(
            "test_patch_object.py", """
            from unittest.mock import patch
            
            class MyService:
                def existing_method(self): pass
                
            def test_foo():
                with patch.object(MyService, <error descr="Unresolved attribute reference 'non_existing' for type 'MyService'">"non_existing"</error>):
                    pass
        """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, false)
    }

    fun testResolvedAttributeOnPatchObject() {
        myFixture.configureByText(
            "test_patch_object_ok.py", """
            from unittest.mock import patch
            
            class MyService:
                def existing_method(self): pass
                
            def test_foo():
                with patch.object(MyService, "existing_method"):
                    pass
        """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, false)
    }

    fun testPatchObjectAsDecorator() {
        myFixture.configureByText(
            "test_patch_decorator.py", """
            from unittest.mock import patch
            
            class MyService:
                def fetch_data(self): pass
                
            @patch.object(MyService, <error descr="Unresolved attribute reference 'missing_method' for type 'MyService'">"missing_method"</error>)
            def test_foo(mock):
                pass
        """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, false)
    }

    fun testPatchObjectWithExistingAttribute() {
        myFixture.configureByText(
            "test_patch_attr.py", """
            from unittest.mock import patch
            
            class MyService:
                name = "service"
                
            @patch.object(MyService, "name")
            def test_foo(mock):
                pass
        """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, false)
    }
}
