package com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject

import fixtures.TestBase
import fixtures.doRefactoringTest

class PyIntroduceParameterObjectMutableDefaultsTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.intellijplatformplugincopy.refactoring.parameterobject.IntroduceParameterObjectRefactoringAction"

    fun testIntroduceWithMutableDefaultList() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                def process_<caret>items(items: list = []):
                    print(items)
                """.trimIndent(),
                """
                from dataclasses import dataclass, field
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class ProcessItemsParams:
                    items: list = field(default_factory=list)
                
                
                def process_items(params: ProcessItemsParams):
                    print(params.items)
                """.trimIndent(),
                actionId
            )
        }
    }

    fun testIntroduceWithMutableDefaultDict() {
        withMockIntroduceParameterObjectDialog {
            myFixture.doRefactoringTest(
                "a.py",
                """
                def process_<caret>config(config: dict = {}):
                    print(config)
                """.trimIndent(),
                """
                from dataclasses import dataclass, field
                from typing import Any
                
                
                @dataclass(frozen=True, slots=True, kw_only=True)
                class ProcessConfigParams:
                    config: dict = field(default_factory=dict)
                
                
                def process_config(params: ProcessConfigParams):
                    print(params.config)
                """.trimIndent(),
                actionId
            )
        }
    }

}
