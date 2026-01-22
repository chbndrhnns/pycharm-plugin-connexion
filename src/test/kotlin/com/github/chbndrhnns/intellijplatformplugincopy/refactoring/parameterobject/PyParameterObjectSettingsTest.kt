package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase
import fixtures.assertActionNotAvailable

class PyParameterObjectSettingsTest : TestBase() {

    private val introduceActionId =
        INTRODUCE_PARAMETER_OBJECT_ACTION_ID
    private val inlineActionId =
        INLINE_PARAMETER_OBJECT_ACTION_ID

    fun testParameterObjectRefactoringDisabled_IntroduceNotAvailable() {
        withPluginSettings({ parameterObject.enableParameterObjectRefactoring = false }) {
            myFixture.assertActionNotAvailable(
                "a.py",
                """
                def foo(a<caret>, b):
                    pass
                """.trimIndent(),
                introduceActionId
            )
        }
    }

    fun testParameterObjectRefactoringDisabled_InlineNotAvailable() {
        withPluginSettings({ parameterObject.enableParameterObjectRefactoring = false }) {
            myFixture.assertActionNotAvailable(
                "a.py",
                """
                from dataclasses import dataclass

                @dataclass
                class Params:
                    a: int
                    b: int

                def foo(par<caret>ams: Params):
                    pass
                """.trimIndent(),
                inlineActionId
            )
        }
    }
}
