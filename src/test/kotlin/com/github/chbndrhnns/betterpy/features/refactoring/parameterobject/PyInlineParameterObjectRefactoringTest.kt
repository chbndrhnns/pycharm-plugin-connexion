package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import fixtures.TestBase
import fixtures.doRefactoringTest

class PyInlineParameterObjectRefactoringTest : TestBase() {

    private val actionId = INLINE_PARAMETER_OBJECT_ACTION_ID

    fun testSimpleInlineParameterObject() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class CreateUserParams:
                first_name: Any
                last_name: Any
                email: Any
                age: Any


            def create_<caret>user(params: CreateUserParams):
                print(params.first_name, params.last_name, params.email, params.age)


            def main():
                create_user(CreateUserParams(first_name="John", last_name="Doe", email="john@example.com", age=30))
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any


            def create_user(first_name: Any, last_name: Any, email: Any, age: Any):
                print(first_name, last_name, email, age)


            def main():
                create_user(first_name="John", last_name="Doe", email="john@example.com", age=30)
            """.trimIndent(),
            actionId
        )
    }

    fun testInlineParameterObjectFromParameterName() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class CreateUserParams:
                first_name: Any
                last_name: Any
                email: Any
                age: Any


            def create_user(<caret>params: CreateUserParams):
                print(params.first_name, params.last_name, params.email, params.age)


            def main():
                create_user(CreateUserParams(first_name="John", last_name="Doe", email="john@example.com", age=30))
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any


            def create_user(first_name: Any, last_name: Any, email: Any, age: Any):
                print(first_name, last_name, email, age)


            def main():
                create_user(first_name="John", last_name="Doe", email="john@example.com", age=30)
            """.trimIndent(),
            actionId
        )
    }

    fun testInlineParameterObjectFromAnnotation() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class CreateUserParams:
                first_name: Any
                last_name: Any
                email: Any
                age: Any


            def create_user(params: Create<caret>UserParams):
                print(params.first_name, params.last_name, params.email, params.age)


            def main():
                create_user(CreateUserParams(first_name="John", last_name="Doe", email="john@example.com", age=30))
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any


            def create_user(first_name: Any, last_name: Any, email: Any, age: Any):
                print(first_name, last_name, email, age)


            def main():
                create_user(first_name="John", last_name="Doe", email="john@example.com", age=30)
            """.trimIndent(),
            actionId
        )
    }

    fun testInlineParameterObjectFromArgumentList() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class CreateUserParams:
                first_name: Any
                last_name: Any
                email: Any
                age: Any


            def create_user(<caret>params: CreateUserParams):
                print(params.first_name, params.last_name, params.email, params.age)


            def main():
                create_user(CreateUserParams(first_name="John", last_name="Doe", email="john@example.com", age=30))
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any


            def create_user(first_name: Any, last_name: Any, email: Any, age: Any):
                print(first_name, last_name, email, age)


            def main():
                create_user(first_name="John", last_name="Doe", email="john@example.com", age=30)
            """.trimIndent(),
            actionId
        )
    }

    fun testInlineParameterObjectFromMiddleOfAnnotation() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class FooParams1:
                first_name: Any
                last_name: Any
                email: Any
                age: Any


            def foo(params: FooPar<caret>ams1):
                print(params.first_name, params.last_name, params.email, params.age)


            def main():
                foo(FooParams1(first_name="John", last_name="Doe", email="john@example.com", age=30))
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any


            def foo(first_name: Any, last_name: Any, email: Any, age: Any):
                print(first_name, last_name, email, age)


            def main():
                foo(first_name="John", last_name="Doe", email="john@example.com", age=30)
            """.trimIndent(),
            actionId
        )
    }

    fun testInlineParameterObjectFromOptionalAnnotation() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any, Optional


            @dataclass(frozen=True, slots=True, kw_only=True)
            class FooParams:
                first_name: Any
                last_name: Any


            def foo(params: Optional[Foo<caret>Params]):
                print(params.first_name, params.last_name)


            def main():
                foo(FooParams(first_name="John", last_name="Doe"))
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any, Optional


            def foo(first_name: Any, last_name: Any):
                print(first_name, last_name)


            def main():
                foo(first_name="John", last_name="Doe")
            """.trimIndent(),
            actionId
        )
    }

    fun testInlineParameterObjectFromUnionAnnotation() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any, Union


            @dataclass(frozen=True, slots=True, kw_only=True)
            class FooParams:
                first_name: Any
                last_name: Any


            def foo(params: Union[Foo<caret>Params, None]):
                print(params.first_name, params.last_name)


            def main():
                foo(FooParams(first_name="John", last_name="Doe"))
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any, Union


            def foo(first_name: Any, last_name: Any):
                print(first_name, last_name)


            def main():
                foo(first_name="John", last_name="Doe")
            """.trimIndent(),
            actionId
        )
    }

    fun testInlineParameterObjectPreservesPositionalOrderWithTrailingArgs() {
        myFixture.doRefactoringTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class FooParams:
                first_name: Any
                last_name: Any


            def foo(<caret>params: FooParams, age: Any):
                print(params.first_name, params.last_name, age)


            def main():
                foo(FooParams(first_name="John", last_name="Doe"), 42)
            """.trimIndent(),
            """
            from dataclasses import dataclass
            from typing import Any


            def foo(first_name: Any, last_name: Any, age: Any):
                print(first_name, last_name, age)


            def main():
                foo("John", "Doe", 42)
            """.trimIndent(),
            actionId
        )
    }

    fun testActionAvailabilityInMiddleOfAnnotation() {
        myFixture.configureByText(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import Any


            @dataclass(frozen=True, slots=True, kw_only=True)
            class FooParams1:
                first_name: Any
                last_name: Any
                email: Any
                age: Any


            def foo(params: FooPar<caret>ams1):
                print(params.first_name, params.last_name, params.email, params.age)


            def main():
                foo(FooParams1(first_name="John", last_name="Doe", email="john@example.com", age=30))
            """.trimIndent()
        )

        val action = ActionManager.getInstance().getAction(actionId)
            ?: throw AssertionError("Action with ID '$actionId' not found")

        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()

        val event = AnActionEvent.createEvent(
            dataContext,
            action.templatePresentation.clone(),
            "unknown",
            ActionUiKind.NONE,
            null
        )

        action.update(event)

        assertTrue(
            "Action should be available when caret is in the middle of annotation",
            event.presentation.isEnabledAndVisible
        )
    }

}
