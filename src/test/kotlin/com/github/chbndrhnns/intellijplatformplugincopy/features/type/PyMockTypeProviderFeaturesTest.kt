package com.github.chbndrhnns.intellijplatformplugincopy.features.type

import com.github.chbndrhnns.intellijplatformplugincopy.features.inspections.PyMockReturnAssignmentInspection
import com.github.chbndrhnns.intellijplatformplugincopy.features.inspections.PyMockUnresolvedReferenceInspection
import fixtures.TestBase

class PyMockTypeProviderFeaturesTest : TestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(
            PyMockUnresolvedReferenceInspection::class.java,
            PyMockReturnAssignmentInspection::class.java
        )
    }

    fun testUnresolvedMemberOnSpec() {
        myFixture.configureByText(
            "test_unresolved.py", """
            from unittest.mock import Mock
            
            class Foo:
                def existing(self): pass
                
            def test_foo():
                m = Mock(spec=Foo)
                m.existing()
                # We expect unresolved reference here. 
                # Note: The exact error description might vary, so checking for any error on 'non_existing'
                m.<error descr="Unresolved attribute reference 'non_existing' for class 'Mock(spec=Foo)'">non_existing</error>
        """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, false)
    }

    fun testMockAttributesOnMethod() {
        myFixture.configureByText(
            "test_mock_attrs.py", """
            from unittest.mock import Mock
            
            class Foo:
                def method(self) -> int: ...
            
            def test_method():    
                m = Mock(spec=Foo)
                # These attributes should be resolved if we handle them
                m.method.assert_called_with()
                print(m.method.call_count)
        """.trimIndent()
        )
        // Just checking that we don't have unresolved references for assert_called_with/call_count
        myFixture.checkHighlighting(true, false, false)
    }

    fun testReturnValueTypeCheck() {
        myFixture.configureByText(
            "test_return_value.py", """
            from unittest.mock import Mock
            
            class Foo:
                def method(self) -> int: ...
            
            def test_rv():    
                m = Mock(spec=Foo)
                m.method.return_value = 1
                # Expected type int, got str
                m.method.return_value = <warning descr="Expected type 'int', got 'str' instead">"str"</warning>
        """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, false)
    }

    fun testMockPassedAsArgumentNoWarning() {
        // This test verifies that passing a Mock(spec=ExternalService) to a function expecting
        // ExternalService does NOT produce a type warning. The fix in PyMockType.getCallType()
        // ensures Mock objects return themselves when called, not the spec's return type.
        myFixture.configureByText(
            "test_mock_arg.py", """
            from unittest.mock import Mock
            
            class ExternalService:
                def fetch_data(self, user_id: int) -> dict: ...
            
            class UserService:
                def __init__(self, service: ExternalService):
                    self.service = service
                    
                def is_user_active(self, user_id: int) -> bool:
                    data = self.service.fetch_data(user_id)
                    return data.get("active", False)
            
            def test_mock_with_mock_object():
                mock_service = Mock(spec=ExternalService)
                mock_service.fetch_data.return_value = {"name": "Jane Smith", "active": False}
                user_service = UserService(mock_service)
                
                is_active = user_service.is_user_active(456)
                
                assert is_active is False
                mock_service.fetch_data.assert_called_once_with(456)
        """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, false)
    }
}
