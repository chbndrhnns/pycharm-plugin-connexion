package com.github.chbndrhnns.intellijplatformplugincopy.refactoring

import fixtures.TestBase

class PyNewTypeRenameTest : TestBase() {

    fun testRenameNewTypeFirstArgument() {
        myFixture.configureByText(
            "test.py", """
            from typing import NewType
            
            UserId = NewType("<caret>UserId", int)
            
            def get_user(user_id: UserId):
                pass
        """.trimIndent()
        )

        myFixture.renameElementAtCaret("NewUserId")

        myFixture.checkResult(
            """
            from typing import NewType
            
            NewUserId = NewType("NewUserId", int)
            
            def get_user(user_id: NewUserId):
                pass
        """.trimIndent()
        )
    }

    fun testRenameNewTypeVariable() {
        myFixture.configureByText(
            "test.py", """
            from typing import NewType
            
            <caret>UserId = NewType("UserId", int)
            
            def get_user(user_id: UserId):
                pass
        """.trimIndent()
        )

        myFixture.renameElementAtCaret("NewUserId")

        myFixture.checkResult(
            """
            from typing import NewType
            
            NewUserId = NewType("NewUserId", int)
            
            def get_user(user_id: NewUserId):
                pass
        """.trimIndent()
        )
    }

    fun testRenameTypeVarFirstArgument() {
        myFixture.configureByText(
            "test.py", """
            from typing import TypeVar
            
            T = TypeVar("<caret>T")
            
            def get_item(item: T) -> T:
                return item
        """.trimIndent()
        )

        myFixture.renameElementAtCaret("U")

        myFixture.checkResult(
            """
            from typing import TypeVar
            
            U = TypeVar("U")
            
            def get_item(item: U) -> U:
                return item
        """.trimIndent()
        )
    }

    fun testRenameTypeVarVariable() {
        myFixture.configureByText(
            "test.py", """
            from typing import TypeVar
            
            <caret>T = TypeVar("T")
            
            def get_item(item: T) -> T:
                return item
        """.trimIndent()
        )

        myFixture.renameElementAtCaret("U")

        myFixture.checkResult(
            """
            from typing import TypeVar
            
            U = TypeVar("U")
            
            def get_item(item: U) -> U:
                return item
        """.trimIndent()
        )
    }

    fun testRenameDisabled_DoesNotUpdateLiteral() {
        val settings = com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState.instance()
        val originalValue = settings.state.enableNewTypeTypeVarParamSpecRename
        settings.state.enableNewTypeTypeVarParamSpecRename = false
        try {
            myFixture.configureByText(
                "test.py", """
                from typing import TypeVar
                
                <caret>T = TypeVar("T")
                
                def get_item(item: T) -> T:
                    return item
            """.trimIndent()
            )

            myFixture.renameElementAtCaret("U")

            // Should NOT rename "T" inside TypeVar
            myFixture.checkResult(
                """
                from typing import TypeVar
                
                U = TypeVar("T")
                
                def get_item(item: U) -> U:
                    return item
            """.trimIndent()
            )
        } finally {
            settings.state.enableNewTypeTypeVarParamSpecRename = originalValue
        }
    }
}
