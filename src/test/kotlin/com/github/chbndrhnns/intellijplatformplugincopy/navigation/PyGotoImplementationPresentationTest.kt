package com.github.chbndrhnns.intellijplatformplugincopy.navigation

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.navigation.GotoTargetPresentationProvider
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PyGotoImplementationPresentationTest : TestBase() {

    fun testDisabledSettingReturnsNull() {
        myFixture.configureByText(
            "a.py",
            """
            class Base:
                def execute(self):
                    pass
            """.trimIndent()
        )

        PluginSettingsState.instance().state.enablePyGotoTargetPresentation = false

        val provider =
            GotoTargetPresentationProvider.EP_NAME.extensionList.find { it is PyGotoTargetPresentationProvider }
        val execute = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java).first()

        val presentation = provider!!.getTargetPresentation(execute, false)
        assertNull("Presentation should be null when setting is disabled", presentation)
    }

    fun testGotoImplementationPresentationWithSameMethodNames() {
        myFixture.configureByText(
            "a.py",
            """
            class Base:
                def exec<caret>ute(self):
                    pass

            class Impl1(Base):
                def execute(self):
                    pass

            class Impl2(Base):
                def execute(self):
                    pass
            """.trimIndent()
        )

        val provider =
            GotoTargetPresentationProvider.EP_NAME.extensionList.find { it is PyGotoTargetPresentationProvider }
        assertNotNull("PyGotoTargetPresentationProvider should be registered", provider)

        val functions = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java)
        val impl1 = functions.find { it.containingClass?.name == "Impl1" }!!
        val impl2 = functions.find { it.containingClass?.name == "Impl2" }!!

        val presentation1 = provider!!.getTargetPresentation(impl1, false)
        assertNotNull(presentation1)
        assertEquals("Impl1.execute", presentation1!!.presentableText)

        val presentation2 = provider.getTargetPresentation(impl2, false)
        assertNotNull(presentation2)
        assertEquals("Impl2.execute", presentation2!!.presentableText)
    }

    fun testGotoImplementationPresentationWithDifferentMethodNames() {
        myFixture.configureByText(
            "a.py",
            """
            class Base:
                def execute(self):
                    pass

                def other(self):
                    pass
            """.trimIndent()
        )

        val provider =
            GotoTargetPresentationProvider.EP_NAME.extensionList.find { it is PyGotoTargetPresentationProvider }
        val functions = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java)
        val execute = functions.find { it.name == "execute" }!!

        val presentation = provider!!.getTargetPresentation(execute, true)
        assertNotNull(presentation)
        assertEquals("execute", presentation!!.presentableText)
    }
}
