package com.github.chbndrhnns.betterpy.features.refactoring.move

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PyMoveDeclarationsProcessorTest : TestBase() {

    fun testMoveTopLevelFunctionToNewModuleUpdatesFromImport() {
        myFixture.addFileToProject("pkg/__init__.py", "")
        myFixture.addFileToProject(
            "pkg/consumer.py",
            """
            from pkg.source import helper

            def use():
                return helper()
            """.trimIndent()
        )

        val sourceFile = myFixture.addFileToProject(
            "pkg/source.py",
            """
            def helper():
                return 1

            def other():
                return 2
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(sourceFile.virtualFile)

        val function = PsiTreeUtil.findChildrenOfType(sourceFile, PyFunction::class.java)
            .first { it.name == "helper" }

        val processor = PyMoveDeclarationsProcessor(
            project = project,
            elements = listOf(function),
            targetModulePath = "pkg.target"
        )

        processor.run()

        val expectedSource = """
            def other():
                return 2
            """.trimIndent() + "\n"
        assertEquals(expectedSource, myFixture.file.text)

        val consumerFile = myFixture.findFileInTempDir("pkg/consumer.py")
        val consumerText = myFixture.psiManager.findFile(consumerFile)!!.text
        val expectedConsumer = """
            from pkg.target import helper

            def use():
                return helper()
            """.trimIndent()
        assertEquals(expectedConsumer, consumerText)

        val targetFile = myFixture.findFileInTempDir("pkg/target.py")
        val targetText = myFixture.psiManager.findFile(targetFile)!!.text
        val expectedTarget = """
            def helper():
                return 1
            """.trimIndent() + "\n"
        assertEquals(expectedTarget, targetText)
    }

    fun testMoveTopLevelFunctionToExistingModuleKeepsExistingContent() {
        myFixture.addFileToProject("pkg/__init__.py", "")
        myFixture.addFileToProject(
            "pkg/target.py",
            """
            def existing():
                return 10
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "pkg/consumer.py",
            """
            from pkg.source import helper

            def use():
                return helper()
            """.trimIndent()
        )

        val sourceFile = myFixture.addFileToProject(
            "pkg/source.py",
            """
            def helper():
                return 1
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(sourceFile.virtualFile)

        val function = PsiTreeUtil.findChildrenOfType(sourceFile, PyFunction::class.java)
            .first { it.name == "helper" }

        val processor = PyMoveDeclarationsProcessor(
            project = project,
            elements = listOf(function),
            targetModulePath = "pkg.target"
        )

        processor.run()

        assertEquals("\n", myFixture.file.text)

        val targetFile = myFixture.findFileInTempDir("pkg/target.py")
        val targetText = myFixture.psiManager.findFile(targetFile)!!.text
        val expectedTarget = """
            def existing():
                return 10


            def helper():
                return 1
            """.trimIndent() + "\n"
        assertEquals(expectedTarget, targetText)
    }

    fun testAddsImportInSourceFileWhenUsageRemains() {
        myFixture.addFileToProject("pkg/__init__.py", "")

        val sourceFile = myFixture.addFileToProject(
            "pkg/source.py",
            """
            def helper():
                return 1

            def use():
                return helper()
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(sourceFile.virtualFile)

        val function = PsiTreeUtil.findChildrenOfType(sourceFile, PyFunction::class.java)
            .first { it.name == "helper" }

        val processor = PyMoveDeclarationsProcessor(
            project = project,
            elements = listOf(function),
            targetModulePath = "pkg.target"
        )

        processor.run()

        val expectedSource = """
            from pkg.target import helper


            def use():
                return helper()
            """.trimIndent() + "\n"
        assertEquals(expectedSource, myFixture.file.text)
    }
}
