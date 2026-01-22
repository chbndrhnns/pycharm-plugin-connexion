package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import fixtures.HeavyTestBase

/**
 * Placeholder for a future heavy multi-module variant of the cross-module
 * custom type tests.
 *
 * NOTE: This class now uses [HeavyTestBase], which in turn relies on
 * IdeaTestFixtureFactory to create a **heavy** project. The current
 * implementation still exercises cross-file behaviour within a single module.
 */
class HeavyTest : HeavyTestBase() {

    private val actionId = "com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    private fun performRefactoringAction() {
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction(actionId)
            ?: throw AssertionError("Action $actionId not found")

        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, myFixture.project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()

        val event = TestActionEvent.createEvent(
            action,
            dataContext,
            action.templatePresentation.clone(),
            "",
            ActionUiKind.NONE,
            null
        )
        action.actionPerformed(event)
    }

    fun testDataclassCrossModule_CreateTypeAtUsageSite_AddsImportAtUsageSite() {
        myFixture.configureByText("mod/__init__.py", "")
        myFixture.configureByText(
            "mod/model.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: int
            """.trimIndent(),
        )

        val usagePsi1 = myFixture.configureByText(
            "mod/usage.py",
            """
            from .model import D


            def do():
                D(product_id=12<caret>3)
            """.trimIndent(),
        )

        configureTempDirAsContentAndSourceRoot()
        waitForSmartModeAndHighlight()
        performRefactoringAction()

        myFixture.openFileInEditor(usagePsi1.virtualFile)
        myFixture.doHighlighting()

        myFixture.checkResult(
            """
            from .model import D, ProductId


            def do():
                D(product_id=ProductId(123))
            """.trimIndent()
        )
    }

    fun testDataclassCrossModule_CreateTypeAtDeclarationSite_AddsImportAtUsageSite() {
        myFixture.configureByText("mod/__init__.py", "")
        val usagePsi2 = myFixture.configureByText(
            "mod/usage.py",
            """
            from .model import D


            def do():
                D(product_id=123)
            """.trimIndent(),
        )

        myFixture.configureByText(
            "mod/model.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: in<caret>t
            """.trimIndent(),
        )

        configureTempDirAsContentAndSourceRoot()
        waitForSmartModeAndHighlight()
        performRefactoringAction()

        myFixture.openFileInEditor(usagePsi2.virtualFile)
        myFixture.doHighlighting()

        myFixture.checkResult(
            """
            from .model import D, ProductId


            def do():
                D(product_id=ProductId(123))
            """.trimIndent()
        )
    }

    fun testDataclassCrossModule_CustomTypeLivesWithDeclaration() {
        myFixture.configureByText(
            "model.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: int
            """.trimIndent(),
        )

        myFixture.configureByText(
            "usage.py",
            """
            from model import D


            def do():
                D(product_id=12<caret>3)
            """.trimIndent(),
        )

        configureTempDirAsContentAndSourceRoot()
        waitForSmartModeAndHighlight()
        performRefactoringAction()

        myFixture.checkResult(
            "model.py",
            """
            import dataclasses

            
            class ProductId(int):
                pass
            
            
            @dataclasses.dataclass
            class D:
                product_id: ProductId
            """.trimIndent(), true
        )
    }
}
