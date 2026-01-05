package com.github.chbndrhnns.intellijplatformplugincopy.psi

import com.github.chbndrhnns.intellijplatformplugincopy.inspections.PyMockPatchUnresolvedReferenceInspection
import fixtures.TestBase

class PyMockPatchFQNQuickFixTest : TestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("unittest/mock.py", "def patch(target, new=None): pass")
        myFixture.addFileToProject("unittest/__init__.py", "")
        myFixture.enableInspections(PyMockPatchUnresolvedReferenceInspection::class.java)
    }

    fun testFQNQuickFixForPatchTarget() {
        myFixture.addFileToProject(
            "my_module.py", """
            class MyClass:
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_fix.py", """
            from unittest.mock import patch
            
            @patch('MyCla<caret>ss')
            def test_something(m):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Replace")
        val intention = intentions.find { it.text.contains("Replace 'MyClass' with '") && it.text.contains(".MyClass'") }
        assertNotNull("Replace quickfix should be available for 'MyClass'", intention)
        
        myFixture.launchAction(intention!!)
        val stringLiteral = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), com.jetbrains.python.psi.PyStringLiteralExpression::class.java)
        assertNotNull("String literal should be found", stringLiteral)
        val result = stringLiteral!!.stringValue
        assertTrue("FQN should contain 'my_module.MyClass', but was '$result'", result.contains("my_module.MyClass"))
    }

    fun testFQNQuickFixForPatchTargetFunction() {
        myFixture.addFileToProject(
            "my_funcs.py", """
            def my_func():
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_fix_func.py", """
            from unittest.mock import patch
            
            @patch('my_fun<caret>c')
            def test_something(m):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Replace")
        val intention = intentions.find { it.text.contains("Replace 'my_func' with '") && it.text.contains(".my_func'") }
        assertNotNull("Replace quickfix should be available for 'my_func'", intention)

        myFixture.launchAction(intention!!)
        val stringLiteral = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), com.jetbrains.python.psi.PyStringLiteralExpression::class.java)
        assertNotNull("String literal should be found", stringLiteral)
        val result = stringLiteral!!.stringValue
        assertTrue("FQN should contain 'my_funcs.my_func', but was '$result'", result.contains("my_funcs.my_func"))
    }

    fun testFQNQuickFixForPatchTargetModule() {
        // Use a module name that is definitely not in the current project yet
        myFixture.addFileToProject("pkg/__init__.py", "")
        myFixture.addFileToProject("pkg/another_mod.py", "class SomeClass: pass")

        myFixture.configureByText(
            "test_fix_mod.py", """
            from unittest.mock import patch
            
            @patch('another_mo<caret>d.SomeClass')
            def test_something(m):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Replace")
        val intention = intentions.find { it.text.contains("Replace 'another_mod' with '") && it.text.contains("another_mod'") }
        assertNotNull("Replace quickfix should be available for 'another_mod'", intention)

        myFixture.launchAction(intention!!)
        val stringLiteral = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), com.jetbrains.python.psi.PyStringLiteralExpression::class.java)
        assertNotNull("String literal should be found", stringLiteral)
        val result = stringLiteral!!.stringValue
        assertTrue("FQN should contain 'pkg.another_mod.SomeClass', but was '$result'", result.contains("pkg.another_mod.SomeClass"))
    }

    fun testFQNQuickFixRespectsSourceRootPrefix() {
        myFixture.addFileToProject("src/pkg/__init__.py", "")
        myFixture.addFileToProject("src/pkg/mod.py", "class MyClass: pass")

        // src is a source root
        runWithSourceRoots(listOf(myFixture.findFileInTempDir("src")!!)) {
            // By default enableRestoreSourceRootPrefix is true in TestBase? 
            // Let's check PluginSettingsState default or TestBase.setUp
            
            myFixture.configureByText(
                "test_prefix.py", """
                from unittest.mock import patch
                
                @patch('MyCla<caret>ss')
                def test_something(m):
                    pass
            """.trimIndent()
            )

            val intentions = myFixture.filterAvailableIntentions("BetterPy: Replace")
            val intention = intentions.find { it.text.contains("Replace 'MyClass' with '") }
            assertNotNull("Replace quickfix should be available", intention)
            
            myFixture.launchAction(intention!!)
            val stringLiteral = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), com.jetbrains.python.psi.PyStringLiteralExpression::class.java)
            val result = stringLiteral!!.stringValue
            
            // If enableRestoreSourceRootPrefix is true, it should probably be 'src.pkg.mod.MyClass'
            // if we want to match how PyResolveUtils resolves it.
            assertTrue("FQN should contain 'src.pkg.mod.MyClass', but was '$result'", result.contains("src.pkg.mod.MyClass"))
        }
    }

    fun testFQNQuickFixRespectsSourceRootPrefixDisabled() {
        myFixture.addFileToProject("src/pkg/__init__.py", "")
        myFixture.addFileToProject("src/pkg/mod.py", "class MyClass: pass")

        // src is a source root
        runWithSourceRoots(listOf(myFixture.findFileInTempDir("src")!!)) {
            com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState.instance().state.enableRestoreSourceRootPrefix = false
            try {
                myFixture.configureByText(
                    "test_prefix_disabled.py", """
                    from unittest.mock import patch
                    
                    @patch('MyCla<caret>ss')
                    def test_something(m):
                        pass
                """.trimIndent()
                )

                val intentions = myFixture.filterAvailableIntentions("BetterPy: Replace")
                val intention = intentions.find { it.text.contains("Replace 'MyClass' with '") }
                assertNotNull("Replace quickfix should be available", intention)

                myFixture.launchAction(intention!!)
                val stringLiteral = com.intellij.psi.util.PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), com.jetbrains.python.psi.PyStringLiteralExpression::class.java)
                val result = stringLiteral!!.stringValue

                // If enableRestoreSourceRootPrefix is false, it should be 'pkg.mod.MyClass'
                assertTrue("FQN should contain 'pkg.mod.MyClass', but was '$result'", result.contains("pkg.mod.MyClass"))
                assertFalse("FQN should NOT contain 'src.pkg.mod.MyClass', but was '$result'", result.contains("src.pkg.mod.MyClass"))
            } finally {
                com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState.instance().state.enableRestoreSourceRootPrefix = true
            }
        }
    }

    fun testFQNQuickFixMultipleCandidates() {
        myFixture.addFileToProject("pkg1/mod.py", "class Target: pass")
        myFixture.addFileToProject("pkg2/mod.py", "class Target: pass")

        myFixture.configureByText(
            "test_multiple.py", """
            from unittest.mock import patch
            
            @patch('Tar<caret>get')
            def test_something(m):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Replace 'Target' with FQN...")
        assertNotNull("Multiple candidates quickfix should be available", intentions.find { it.text == "BetterPy: Replace 'Target' with FQN..." })
    }

    fun testFQNQuickFixSuggestsImportSites() {
        // Declaration site
        myFixture.addFileToProject("logic/service.py", "class MyService: pass")
        
        // Import site 1
        myFixture.addFileToProject("app/main.py", "from logic.service import MyService")
        
        // Import site 2
        myFixture.addFileToProject("app/worker.py", "from logic.service import MyService")

        myFixture.configureByText(
            "test_import_sites.py", """
            from unittest.mock import patch
            
            @patch('MyServi<caret>ce')
            def test_something(m):
                pass
        """.trimIndent()
        )

        // Currently it would suggest 'logic.service.MyService'
        // We want it to suggest 'app.main.MyService' and 'app.worker.MyService' as well (or instead?)
        // The issue says: "we need to provide the FQN to the place where the target is IMPORTED, not where it's declared."
        
        val intentions = myFixture.filterAvailableIntentions("BetterPy: Replace 'MyService' with")
        
        // If there are multiple candidates (declaration site + import sites), it should show the "with FQN..." variant.
        val intention = intentions.find { it.text == "BetterPy: Replace 'MyService' with FQN..." }
        assertNotNull("Replace quickfix should be available for 'MyService' with multiple candidates", intention)
    }

    fun testFQNQuickFixDoesNotDuplicateSourceRootPrefix() {
        myFixture.addFileToProject("tests/__init__.py", "")
        myFixture.addFileToProject(
            "tests/test_mymock.py", """
            class MyMyClass:
                pass
        """.trimIndent()
        )

        val testFile = myFixture.addFileToProject(
            "tests/actual_test.py", """
            from unittest.mock import patch
            
            @patch('MyMyCla<caret>ss')
            def test_something(m):
                pass
        """.trimIndent()
        )

        // If tests is the source root, QualifiedNameFinder should return 'test_mymock'
        // If it returns 'tests.test_mymock' AND we prepend 'tests', we get duplication.
        
        runWithSourceRoots(listOf(myFixture.findFileInTempDir("tests")!!)) {
            myFixture.configureFromExistingVirtualFile(testFile.virtualFile)
            
            val intentions = myFixture.filterAvailableIntentions("BetterPy: Replace 'MyMyClass' with")
            val intention = intentions.find { it.text.contains("tests.test_mymock.MyMyClass") }
            
            assertNotNull("Replace quickfix should be available", intention)
            // Verify no duplication
            assertFalse("FQN should not contain duplicated 'tests': ${intention!!.text}", 
                intention.text.contains("tests.tests."))
        }
    }

    fun testFQNQuickFixOnlySuggestsProjectCandidates() {
        // Add a "project" class
        myFixture.addFileToProject(
            "project_module.py", """
            class ProjectClass:
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_project.py", """
            from unittest.mock import patch
            
            @patch('ProjectClas<caret>s')
            def test_something(m):
                pass
        """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("BetterPy: Replace 'ProjectClass' with")
        assertNotEmpty(intentions)
    }

    fun testFQNQuickFixSuggestsThirdPartyUsageInProject() {
        // We'll use 'os.path.exists' which is in the stdlib (library).
        myFixture.addFileToProject("app/utils.py", "from os.path import exists\nusage = exists")
        
        myFixture.configureByText(
            "test_third_party.py", """
            from unittest.mock import patch
            
            @patch('exis<caret>ts')
            def test_something(m):
                pass
        """.trimIndent()
        )
        
        val intentions = myFixture.filterAvailableIntentions("BetterPy: Replace 'exists' with")
        val intention = intentions.find { it.text.contains("app.utils.exists") }
        assertNotNull("Should suggest project-side usage of third-party symbol", intention)
        
        val libraryIntention = intentions.find { it.text.contains("os.path.exists") }
        assertNull("Should NOT suggest library-side FQN", libraryIntention)
    }

    fun testPatchTargetInNonSourceRootFolderShouldResolve() {
        // Issue: patch("tests.test_mymock.ExternalService") shows unresolved reference
        // when tests folder is not marked as source root - but it should still resolve
        // Fix: PyResolveUtils.findTopLevelModule now checks content root, not just source root

        // Declaration site
        myFixture.addFileToProject("services/__init__.py", "")
        myFixture.addFileToProject("services/external.py", "class ExternalService: pass")

        // Usage site - tests folder imports ExternalService (NOT marked as source root)
        myFixture.addFileToProject("tests/__init__.py", "")
        val testFile = myFixture.addFileToProject(
            "tests/test_mymock.py",
            "from services.external import ExternalService"
        )

        // Before the fix, this returned null because only source roots were checked
        val resolved = PyResolveUtils.resolveDottedName("tests.test_mymock.ExternalService", testFile)
        assertTrue("Should resolve path at content root even when not a source root", resolved.isNotEmpty())
    }

    fun testNestedSegmentUnresolvedHighlighting() {
        myFixture.addFileToProject("logic/__init__.py", "")
        myFixture.addFileToProject("logic/service.py", "class MyService: pass")
        
        myFixture.configureByText(
            "test_nested_unresolved.py", """
            from unittest.mock import patch
            
            @patch('logic.servi1ce.MyService')
            def test_something(m):
                pass
        """.trimIndent()
        )
        
        val highlights = myFixture.doHighlighting()
        val unresolvedHighlight = highlights.find { it.description == "Unresolved reference 'servi1ce' in patch target" }
        assertNotNull("Highlight should be present for unresolved nested segment 'servi1ce'", unresolvedHighlight)
    }
}
