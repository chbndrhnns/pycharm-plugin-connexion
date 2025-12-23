package com.github.chbndrhnns.intellijplatformplugincopy.run

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import fixtures.TestBase

class PyPackageRunLineMarkerContributorTest : TestBase() {

    fun testGutterIconAvailableForMain() {
        myFixture.addFileToProject("pkg/__init__.py", "")
        val file = myFixture.addFileToProject(
            "pkg/app.py", """
            if __name__ == "__main__":
                print("hello")
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val ifKeyword = PsiTreeUtil.findChildrenOfType(myFixture.file, com.intellij.psi.PsiElement::class.java)
            .find { it.node.elementType == PyTokenTypes.IF_KEYWORD }!!

        val contributor = PyPackageRunLineMarkerContributor()
        val info = contributor.getInfo(ifKeyword)

        assertNotNull("Gutter icon should be available for __main__", info)
    }

    fun testGutterIconNotAvailableWhenDisabled() {
        PluginSettingsState.instance().state.enablePyPackageRunConfigurationAction = false

        myFixture.addFileToProject("pkg/__init__.py", "")
        val file = myFixture.addFileToProject(
            "pkg/app_disabled.py", """
            if __name__ == "__main__":
                print("hello")
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val ifKeyword = PsiTreeUtil.findChildrenOfType(myFixture.file, com.intellij.psi.PsiElement::class.java)
            .find { it.node.elementType == PyTokenTypes.IF_KEYWORD }!!

        val contributor = PyPackageRunLineMarkerContributor()
        val info = contributor.getInfo(ifKeyword)

        assertNull("Gutter icon should NOT be available when disabled", info)
    }

    fun testGutterIconNotAvailableForRegularIf() {
        myFixture.addFileToProject("pkg/__init__.py", "")
        val file = myFixture.addFileToProject(
            "pkg/app_regular.py", """
            if True:
                print("hello")
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val ifKeyword = PsiTreeUtil.findChildrenOfType(myFixture.file, com.intellij.psi.PsiElement::class.java)
            .find { it.node.elementType == PyTokenTypes.IF_KEYWORD }!!

        val contributor = PyPackageRunLineMarkerContributor()
        val info = contributor.getInfo(ifKeyword)

        assertNull("Gutter icon should NOT be available for regular if", info)
    }
}
