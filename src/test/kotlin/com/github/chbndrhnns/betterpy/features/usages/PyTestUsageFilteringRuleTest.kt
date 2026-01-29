package com.github.chbndrhnns.betterpy.features.usages

import com.intellij.openapi.components.service
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usages.UsageInfo2UsageAdapter
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PyTestUsageFilteringRuleTest : TestBase() {

    fun testRuleAllowsOnlyTestSourceUsagesWhenEnabled() {
        val srcDir = myFixture.tempDirFixture.findOrCreateDir("src")
        val testDir = myFixture.tempDirFixture.findOrCreateDir("tests")
        runWithSourceRoots(listOf(srcDir)) {
            runWithTestSourceRoots(listOf(testDir)) {
                myFixture.addFileToProject(
                    "src/app.py",
                    """
                    def foo():
                        pass
                    """.trimIndent()
                )
                myFixture.addFileToProject(
                    "src/use.py",
                    """
                    import app
                    app.foo()
                    """.trimIndent()
                )
                myFixture.addFileToProject(
                    "tests/test_use.py",
                    """
                    import app
                    app.foo()
                    """.trimIndent()
                )

                val appVFile = myFixture.tempDirFixture.getFile("src/app.py")!!
                val appFile = PsiManager.getInstance(project).findFile(appVFile)!!
                val targetElement = PsiTreeUtil.findChildOfType(appFile, PyFunction::class.java)
                assertNotNull(targetElement)
                val usageInfos = myFixture.findUsages(targetElement!!)
                val usages = usageInfos.map { UsageInfo2UsageAdapter(it) }

                project.service<PyTestUsageFilteringStateService>().showOnlyTestUsages = true
                val rule = PyTestUsageFilteringRule(project)
                val visibilityByFile = usages.associate { usage ->
                    val fileName = usage.element?.containingFile?.name ?: "<null>"
                    fileName to rule.isVisible(usage)
                }

                assertEquals(false, visibilityByFile["use.py"])
                assertEquals(true, visibilityByFile["test_use.py"])
            }
        }
    }

    fun testProviderDisabledByFeatureFlag() {
        val state = com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState.instance().state
        val previous = state.enableTestUsageFilteringRule
        try {
            state.enableTestUsageFilteringRule = false
            val provider = PyTestUsageFilteringRuleProvider()
            val rules = provider.getApplicableRules(project)
            assertTrue(rules.isEmpty())
        } finally {
            state.enableTestUsageFilteringRule = previous
        }
    }
}
