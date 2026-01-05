package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase
import fixtures.assertActionNotAvailable

class PyParameterObjectSettingsTest : TestBase() {

    private val introduceActionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.IntroduceParameterObjectRefactoringAction"
    private val inlineActionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.InlineParameterObjectRefactoringAction"

    fun testParameterObjectRefactoringDisabled_IntroduceNotAvailable() {
        withPluginSettings({ enableParameterObjectRefactoring = false }) {
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
        withPluginSettings({ enableParameterObjectRefactoring = false }) {
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
