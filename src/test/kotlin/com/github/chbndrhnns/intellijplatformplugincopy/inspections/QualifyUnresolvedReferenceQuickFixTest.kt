package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import fixtures.TestBase

class QualifyUnresolvedReferenceQuickFixTest : TestBase() {

    override fun setUp() {
        super.setUp()
        PluginSettingsState.instance().state.enableUnresolvedReferenceAsErrorInspection = true
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
    }

    fun testQualifyUnresolvedReferenceAbsoluteImport() {
        myFixture.addFileToProject(
            "domain.py", """
            class MyClass:
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "usage.py", """
            import domain

            MyClas<caret>s
        """.trimIndent()
        )

        val intentionName = "Qualify with 'domain.'"
        val intentions = myFixture.filterAvailableIntentions(intentionName)

        assertNotEmpty(intentions)
        myFixture.launchAction(intentions.first())

        myFixture.checkResult(
            """
            import domain

            domain.MyClass
        """.trimIndent()
        )
    }

    fun testQualifyUnresolvedReferenceFromImport() {
        myFixture.addFileToProject(
            "domain.py", """
            class MyClass:
                pass
            def my_func():
                pass
            my_attr = 1
        """.trimIndent()
        )

        myFixture.configureByText(
            "usage.py", """
            from domain import my_attr

            MyClass<caret>
            my_func()
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Qualify with 'domain.'")
        assertNotEmpty(intentions)
        myFixture.launchAction(intentions.first())

        myFixture.checkResult(
            """
            from domain import my_attr

            domain.MyClass
            my_func()
        """.trimIndent()
        )
    }

    fun testQualifyUnresolvedReferenceRelativeImport() {
        myFixture.addFileToProject("pkg/__init__.py", "")
        myFixture.addFileToProject(
            "pkg/domain.py", """
            class MyClass:
                pass
        """.trimIndent()
        )

        val usageFile = myFixture.addFileToProject(
            "pkg/usage.py", """
            from . import domain

            MyClass<caret>
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(usageFile.virtualFile)

        val intentions = myFixture.filterAvailableIntentions("Qualify with 'domain.'")
        assertNotEmpty(intentions)
        myFixture.launchAction(intentions.first())

        myFixture.checkResult(
            """
            from . import domain

            domain.MyClass
        """.trimIndent()
        )
    }

    fun testQualifyUnresolvedReferenceRelativeFromImport() {
        myFixture.addFileToProject("pkg/__init__.py", "")
        myFixture.addFileToProject("pkg/sub/__init__.py", "")
        myFixture.addFileToProject(
            "pkg/sub/domain.py", """
            class MyClass:
                pass
        """.trimIndent()
        )

        val usageFile = myFixture.addFileToProject(
            "pkg/usage.py", """
            from .sub import domain

            MyClass<caret>
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(usageFile.virtualFile)

        val intentions = myFixture.filterAvailableIntentions("Qualify with 'domain.'")
        assertNotEmpty(intentions)
        myFixture.launchAction(intentions.first())

        myFixture.checkResult(
            """
            from .sub import domain

            domain.MyClass
        """.trimIndent()
        )
    }
}
