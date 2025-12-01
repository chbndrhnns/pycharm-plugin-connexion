package com.github.chbndrhnns.intellijplatformplugincopy.imports

import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import fixtures.TestBase

class RelativeImportTest : TestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    }

    fun testRelativeImportPreference() {
        // Create a sibling module with a symbol
        myFixture.addFileToProject("pkg/__init__.py", "")
        myFixture.addFileToProject("pkg/sibling.py", "def foo(): pass")

        // Create the file where we want to import
        // myFixture.configureByText("pkg/main.py", "foo<caret>()") <-- this failed with Invalid file name

        val content = "foo<caret>()"
        val fileContent = content.replace("<caret>", "")
        val file = myFixture.addFileToProject("pkg/main.py", fileContent)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(content.indexOf("<caret>"))

        // Get available intentions
        // Since we hooked in, we expect a relative import option to be available.
        // The text might be "Import 'foo from .sibling'" or similar
        
        val intentions = myFixture.availableIntentions
        var relativeOptionFound = false
        
        for (action in intentions) {
            if (action.text.contains("from .sibling")) {
                relativeOptionFound = true
                myFixture.launchAction(action)
                break
            }
        }

        if (relativeOptionFound) {
            myFixture.checkResult("from .sibling import foo\n\nfoo()")
        } else {
            // Fallback: Try to invoke the standard 'Import' and see if it picked the relative one (preference)
            // If multiple import options exist, "Import" action usually opens a popup which we can't easily verify here.
            // But if our provider puts the candidate first, or if it's the only one...
            
             val importAction = myFixture.filterAvailableIntentions("Import").firstOrNull()
             if (importAction != null) {
                 myFixture.launchAction(importAction)
                 // NOTE: This assertion might fail if the absolute one is still preferred by some internal logic,
                 // but based on sorting, relative should be first.
                 myFixture.checkResult("from .sibling import foo\n\nfoo()")
             } else {
                 fail("No 'Import' action found. Available: " + intentions.map { it.text })
             }
        }
    }

    fun testRelativeImportFromSubpackage() {
        // Create structure
        // pkg/
        //   __init__.py
        //   main.py
        //   myp/
        //     __init__.py
        //     _other.py -> class Other
        
        myFixture.addFileToProject("pkg/__init__.py", "")
        myFixture.addFileToProject("pkg/myp/__init__.py", "")
        myFixture.addFileToProject("pkg/myp/_other.py", "class Other: pass")

        val content = "Other<caret>"
        val file = myFixture.addFileToProject("pkg/main.py", content)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        
        // Trigger auto-import
        // We expect "Import 'pkg.myp._other.Other'" and our hook to add "from .myp._other import Other"
        myFixture.filterAvailableIntentions("Import")
        // Search for the specific relative import text
        // It might appear as "from .myp._other import Other" in the quickfix text or popup
        
        var relativeOptionFound = false
        val expectedImportText = "from .myp._other"
        
        // Sometimes the intention text is "Import 'Other' from .myp._other" or similar.
        // Or it might be just "Import 'Other'" and the candidate is inside.
        
        // Let's check available intentions text first.
        val allIntentions = myFixture.availableIntentions
        for (action in allIntentions) {
            if (action.text.contains(expectedImportText)) {
                relativeOptionFound = true
                myFixture.launchAction(action)
                break
            }
        }
        
        if (!relativeOptionFound) {
             // If not found directly, maybe check if 'Import' action exists and if applying it gives the relative import
             // prioritizing relative import if our setting is on.
             val importAction = myFixture.filterAvailableIntentions("Import").firstOrNull()
             if (importAction != null) {
                 myFixture.launchAction(importAction)
                 // Check result
                 myFixture.checkResult("from .myp._other import Other\n\nOther")
             } else {
                 fail("Relative import option not found and no generic Import action available. Available: " + allIntentions.map { it.text })
             }
        } else {
             myFixture.checkResult("from .myp._other import Other\n\nOther")
        }
    }
}
