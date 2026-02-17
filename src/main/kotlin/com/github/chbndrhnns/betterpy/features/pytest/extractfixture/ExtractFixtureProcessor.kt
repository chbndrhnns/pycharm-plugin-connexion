package com.github.chbndrhnns.betterpy.features.pytest.extractfixture

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.*

class ExtractFixtureProcessor(
    project: Project,
    private val file: PyFile,
    private val model: ExtractFixtureModel,
    private val options: ExtractFixtureOptions
) : BaseRefactoringProcessor(project) {

    private val containingFunction: PyFunction = model.containingFunction
    private val fixtureName: String = options.fixtureName

    override fun getCommandName(): String = "Extract Pytest Fixture"

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return object : UsageViewDescriptor {
            override fun getElements(): Array<PsiElement> = arrayOf(containingFunction)
            override fun getProcessedElementsHeader(): String = "Extract fixture"
            override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String = ""
        }
    }

    override fun findUsages(): Array<UsageInfo> = emptyArray()

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val generator = PyElementGenerator.getInstance(myProject)

        val selectedElements = model.statements
        if (selectedElements.isEmpty()) return

        val usedFixtures = model.usedFixtures.map { it.name }
        val paramList = usedFixtures.joinToString(", ")
        val selectedText = selectedElements.joinToString("\n") { it.text }
        val returnStatement = buildReturnStatement(selectedElements)

        when (options.extractionMode) {
            ExtractionMode.FIXTURE -> extractAsFixture(
                generator,
                paramList,
                selectedText,
                returnStatement,
                usedFixtures
            )

            ExtractionMode.HELPER_FUNCTION -> extractAsHelperFunction(
                generator,
                paramList,
                selectedText,
                returnStatement,
                usedFixtures
            )
        }

        CodeStyleManager.getInstance(myProject).reformat(file)

        // Also reformat conftest.py if we extracted there
        if (options.targetLocation == TargetLocation.CONFTEST) {
            findOrCreateConftestFile()?.let { conftest ->
                CodeStyleManager.getInstance(myProject).reformat(conftest)
            }
        }
    }

    private fun extractAsFixture(
        generator: PyElementGenerator,
        paramList: String,
        selectedText: String,
        returnStatement: String?,
        usedFixtures: List<String>
    ) {
        val decoratorArgs = buildList {
            if (options.fixtureScope != FixtureScope.FUNCTION) {
                add("scope=\"${options.fixtureScope.value}\"")
            }
            if (options.autouse) {
                add("autouse=True")
            }
        }
        val decoratorSuffix = if (decoratorArgs.isNotEmpty()) "(${decoratorArgs.joinToString(", ")})" else ""

        val fixtureCode = buildString {
            appendLine("@pytest.fixture$decoratorSuffix")
            appendLine("def $fixtureName($paramList):")
            selectedText.lines().forEach { line ->
                appendLine("    $line")
            }
            if (returnStatement != null) {
                appendLine("    return $returnStatement")
            }
        }

        when (options.targetLocation) {
            TargetLocation.SAME_FILE -> {
                val newFixture = generator.createFromText(
                    LanguageLevel.PYTHON311,
                    PyFunction::class.java,
                    fixtureCode.trimEnd()
                )
                // Find top-level anchor (class or function) that is a direct child of the file
                val topLevelAnchor = findTopLevelAnchor(containingFunction)
                file.addBefore(newFixture, topLevelAnchor)
                file.addBefore(generator.createNewLine(), topLevelAnchor)
                file.addBefore(generator.createNewLine(), topLevelAnchor)
            }

            TargetLocation.CONFTEST -> {
                val conftestFile = findOrCreateConftestFile()
                if (conftestFile != null) {
                    addFixtureToFile(conftestFile, generator, fixtureCode)
                }
            }

            TargetLocation.OTHER_FILE -> {
                // TODO: Implement file chooser for other file extraction
                val newFixture = generator.createFromText(
                    LanguageLevel.PYTHON311,
                    PyFunction::class.java,
                    fixtureCode.trimEnd()
                )
                file.addBefore(newFixture, containingFunction)
                file.addBefore(generator.createNewLine(), containingFunction)
                file.addBefore(generator.createNewLine(), containingFunction)
            }
        }

        updateOriginalFunctionForFixture(usedFixtures, generator)
    }

    private fun extractAsHelperFunction(
        generator: PyElementGenerator,
        paramList: String,
        selectedText: String,
        returnStatement: String?,
        usedFixtures: List<String>
    ) {
        val functionCode = buildString {
            appendLine("def $fixtureName($paramList):")
            selectedText.lines().forEach { line ->
                appendLine("    $line")
            }
            if (returnStatement != null) {
                appendLine("    return $returnStatement")
            }
        }

        val newFunction = generator.createFromText(
            LanguageLevel.PYTHON311,
            PyFunction::class.java,
            functionCode.trimEnd()
        )

        // Find top-level anchor (class or function) that is a direct child of the file
        val topLevelAnchor = findTopLevelAnchor(containingFunction)
        file.addBefore(newFunction, topLevelAnchor)
        file.addBefore(generator.createNewLine(), topLevelAnchor)
        file.addBefore(generator.createNewLine(), topLevelAnchor)

        updateOriginalFunctionForHelper(usedFixtures, returnStatement, generator)
    }

    /**
     * Find the top-level element (direct child of file) that contains the given function.
     * For class methods, this returns the containing class. For top-level functions, returns the function itself.
     */
    private fun findTopLevelAnchor(function: PyFunction): PsiElement {
        var current: PsiElement = function
        while (current.parent != null && current.parent !is PyFile) {
            current = current.parent
        }
        return current
    }

    private fun findOrCreateConftestFile(): PyFile? {
        val directory = file.containingDirectory ?: return null
        val existingConftest = directory.findFile("conftest.py")
        if (existingConftest != null) {
            return existingConftest as? PyFile
        }

        // Create new conftest.py
        val conftestContent = "import pytest\n\n"
        val newFile = PsiFileFactory.getInstance(myProject)
            .createFileFromText("conftest.py", PythonLanguage.getInstance(), conftestContent)
        return directory.add(newFile) as? PyFile
    }

    private fun addFixtureToFile(
        targetFile: PyFile,
        generator: PyElementGenerator,
        fixtureCode: String
    ) {
        val newFixture = generator.createFromText(
            LanguageLevel.PYTHON311,
            PyFunction::class.java,
            fixtureCode.trimEnd()
        )

        // Ensure pytest import exists
        ensurePytestImport(targetFile, generator)

        // Add fixture at end of file
        targetFile.add(generator.createNewLine())
        targetFile.add(generator.createNewLine())
        targetFile.add(newFixture)
    }

    private fun ensurePytestImport(targetFile: PyFile, generator: PyElementGenerator) {
        val hasImport = targetFile.importBlock?.any { stmt ->
            stmt.text.contains("import pytest")
        } ?: false

        if (!hasImport) {
            val importStmt = generator.createImportStatement(
                LanguageLevel.PYTHON311,
                "pytest",
                null
            )
            val firstStatement = targetFile.statements.firstOrNull()
            if (firstStatement != null) {
                targetFile.addBefore(importStmt, firstStatement)
                targetFile.addBefore(generator.createNewLine(), firstStatement)
            } else {
                targetFile.add(importStmt)
            }
        }
    }

    private fun buildReturnStatement(elements: List<PyStatement>): String? {
        val lastElement = elements.lastOrNull() ?: return null

        if (lastElement is PyAssignmentStatement) {
            val target = lastElement.targets.firstOrNull()
            if (target is PyTargetExpression) {
                return target.name
            }
        }

        if (lastElement is PyExpressionStatement) {
            return lastElement.expression?.text
        }

        return null
    }

    private fun updateOriginalFunctionForFixture(
        usedFixtures: List<String>,
        generator: PyElementGenerator
    ) {
        // Remove selected statements
        model.statements.forEach { it.delete() }

        // Update parameters: remove used fixtures
        val paramList = containingFunction.parameterList
        val paramsToKeep = mutableListOf<String>()

        for (param in paramList.parameters) {
            val name = param.name ?: continue
            if (name !in usedFixtures || name == "self" || name == "cls") {
                paramsToKeep.add(param.text)
            }
        }

        // Handle injection mode (skip if autouse since fixture will be auto-injected)
        if (!options.autouse) {
            when (options.injectionMode) {
                InjectionMode.PARAMETER -> {
                    paramsToKeep.add(fixtureName)
                }

                InjectionMode.USEFIXTURES -> {
                    addUsefixuresDecorator(generator)
                }
            }
        }

        val newParamListText = paramsToKeep.joinToString(", ")
        val dummyFunction = generator.createFromText(
            LanguageLevel.PYTHON311,
            PyFunction::class.java,
            "def dummy($newParamListText): pass"
        )
        paramList.replace(dummyFunction.parameterList)
    }

    private fun addUsefixuresDecorator(generator: PyElementGenerator) {
        val decoratorList = containingFunction.decoratorList

        // Check if there's already a usefixtures decorator
        val existingUsefixtures = decoratorList?.decorators?.find { decorator ->
            val callee = decorator.callee?.text ?: decorator.name
            callee == "pytest.mark.usefixtures" || callee?.endsWith(".usefixtures") == true
        }

        if (existingUsefixtures != null) {
            // Append to existing usefixtures decorator
            val argList = existingUsefixtures.argumentList
            if (argList != null) {
                val newArg = generator.createStringLiteralAlreadyEscaped("\"$fixtureName\"")
                argList.addArgument(newArg)
            }
        } else {
            // Create new usefixtures decorator
            val decoratorText = "@pytest.mark.usefixtures(\"$fixtureName\")"
            val dummyFunc = generator.createFromText(
                LanguageLevel.PYTHON311,
                PyFunction::class.java,
                "$decoratorText\ndef dummy(): pass"
            )
            val newDecorator = dummyFunc.decoratorList?.decorators?.first()
            if (newDecorator != null) {
                if (decoratorList != null && decoratorList.decorators.isNotEmpty()) {
                    // Add after existing decorators
                    decoratorList.add(newDecorator)
                } else {
                    // Add before function
                    containingFunction.addBefore(newDecorator, containingFunction.firstChild)
                }
            }
        }
    }

    private fun updateOriginalFunctionForHelper(
        usedFixtures: List<String>,
        returnStatement: String?,
        generator: PyElementGenerator
    ) {
        // Remove selected statements
        model.statements.forEach { it.delete() }

        // Add call to helper function
        val callArgs = usedFixtures.joinToString(", ")
        val callText = if (returnStatement != null) {
            "$returnStatement = $fixtureName($callArgs)"
        } else {
            "$fixtureName($callArgs)"
        }

        val callStatement = generator.createFromText(
            LanguageLevel.PYTHON311,
            PyStatement::class.java,
            callText
        )

        // Insert at the position where selected code was
        val statementList = containingFunction.statementList
        if (statementList.statements.isNotEmpty()) {
            statementList.addBefore(callStatement, statementList.statements.first())
        } else {
            statementList.add(callStatement)
        }
    }
}
