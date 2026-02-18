package com.github.chbndrhnns.betterpy.features.pytest.inlinefixture

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.jetbrains.python.psi.*

class InlineFixtureProcessor(
    project: Project,
    private val model: InlineFixtureModel,
    private val options: InlineFixtureOptions,
    private val targetUsage: FixtureUsage? = null
) : BaseRefactoringProcessor(project) {

    override fun getCommandName(): String = "Inline Pytest Fixture"

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return object : UsageViewDescriptor {
            override fun getElements(): Array<PsiElement> = arrayOf(model.fixtureFunction)
            override fun getProcessedElementsHeader(): String = "Inline fixture"
            override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String = ""
        }
    }

    override fun findUsages(): Array<UsageInfo> = emptyArray()

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val usagesToInline = when (options.inlineMode) {
            InlineMode.INLINE_ALL_AND_REMOVE -> model.usages
            InlineMode.INLINE_THIS_ONLY -> listOfNotNull(targetUsage ?: model.usages.firstOrNull())
        }
        if (usagesToInline.isEmpty()) return

        for (usage in usagesToInline) {
            inlineIntoUsage(usage)
        }

        if (options.inlineMode == InlineMode.INLINE_ALL_AND_REMOVE) {
            model.fixtureFunction.delete()
        }

        val filesToReformat = LinkedHashSet<PyFile>()
        filesToReformat.addAll(usagesToInline.map { it.file })
        val fixtureFile = model.fixtureFunction.containingFile as? PyFile
        if (fixtureFile != null) {
            filesToReformat.add(fixtureFile)
        }
        filesToReformat.forEach { file ->
            CodeStyleManager.getInstance(myProject).reformat(file)
        }
    }

    private fun inlineIntoUsage(usage: FixtureUsage) {
        val function = usage.function
        val generator = PyElementGenerator.getInstance(myProject)
        val fixtureName = model.fixtureName
        val returnExpr = model.returnInfo.returnedExpression

        val inlineStatements = when {
            model.returnInfo.returnStatement != null -> model.returnInfo.statementsBeforeReturn
            else -> model.body
        }

        val returnVarName = (returnExpr as? PyReferenceExpression)
            ?.takeIf { it.qualifier == null }
            ?.name
        val returnVarAssigned = returnVarName != null && isLocalNameAssigned(inlineStatements, returnVarName)

        val renameReturnVar = returnVarName != null && returnVarName != fixtureName && returnVarAssigned

        val copiedStatements = inlineStatements.map { statement ->
            val copy = statement.copy() as PyStatement
            if (renameReturnVar) {
                renameLocalName(copy, requireNotNull(returnVarName), fixtureName, generator)
            }
            copy
        }.toMutableList()

        val assignmentNeeded =
            returnExpr != null && !renameReturnVar && (returnVarName == null || returnVarName != fixtureName)
        if (assignmentNeeded) {
            val assignmentText = "$fixtureName = ${returnExpr.text}"
            val assignment = generator.createFromText(
                LanguageLevel.forElement(function),
                PyStatement::class.java,
                assignmentText
            )
            copiedStatements.add(assignment)
        }

        insertStatements(function, copiedStatements)

        updateFunctionParameters(function, usage.parameter, model.dependencies)
        removeUsefixturesUsage(usage)
        ensureImportsFromFixture(usage.file)
    }

    private fun insertStatements(function: PyFunction, statements: List<PyStatement>) {
        if (statements.isEmpty()) return
        val statementList = function.statementList
        val anchor = findInsertionAnchor(statementList)

        for (statement in statements) {
            if (anchor != null) {
                statementList.addBefore(statement, anchor)
            } else {
                statementList.add(statement)
            }
        }
    }

    private fun findInsertionAnchor(statementList: PyStatementList): PsiElement? {
        val statements = statementList.statements
        if (statements.isEmpty()) return null
        val first = statements.first()
        return if (isDocstringStatement(first)) {
            statements.getOrNull(1)
        } else {
            first
        }
    }

    private fun isDocstringStatement(statement: PyStatement): Boolean {
        val exprStatement = statement as? PyExpressionStatement ?: return false
        return exprStatement.expression is PyStringLiteralExpression
    }

    private fun updateFunctionParameters(
        function: PyFunction,
        removedParameter: PyNamedParameter?,
        dependencies: List<String>
    ) {
        val paramList = function.parameterList
        val paramsToKeep = mutableListOf<String>()
        val existingNames = mutableSetOf<String>()

        for (param in paramList.parameters) {
            val name = param.name ?: continue
            if (removedParameter != null && removedParameter.isEquivalentTo(param)) continue
            paramsToKeep.add(param.text)
            existingNames.add(name)
        }

        dependencies.filter { it !in existingNames }.forEach { paramsToKeep.add(it) }

        val newParamListText = paramsToKeep.joinToString(", ")
        val dummyFunction = PyElementGenerator.getInstance(myProject).createFromText(
            LanguageLevel.forElement(function),
            PyFunction::class.java,
            "def dummy($newParamListText): pass"
        )
        paramList.replace(dummyFunction.parameterList)
    }

    private fun removeUsefixturesUsage(usage: FixtureUsage) {
        val decorator = usage.usefixturesDecorator ?: return
        val argList = decorator.argumentList ?: return
        val args = argList.arguments
        val targetArg = args.firstOrNull {
            it is PyStringLiteralExpression && it.stringValue == model.fixtureName
        } ?: return

        if (args.size == 1) {
            decorator.delete()
        } else {
            targetArg.delete()
        }
    }

    private fun renameLocalName(
        statement: PyStatement,
        oldName: String,
        newName: String,
        generator: PyElementGenerator
    ) {
        PsiTreeUtil.findChildrenOfType(statement, PyReferenceExpression::class.java).forEach { ref ->
            if (ref.qualifier == null && ref.name == oldName) {
                val replacement = generator.createExpressionFromText(
                    LanguageLevel.forElement(statement),
                    newName
                )
                ref.replace(replacement)
            }
        }
        PsiTreeUtil.findChildrenOfType(statement, PyTargetExpression::class.java).forEach { target ->
            if (target.qualifier == null && target.name == oldName) {
                target.setName(newName)
            }
        }
    }

    private fun isLocalNameAssigned(statements: List<PyStatement>, name: String): Boolean {
        return statements.any { statement ->
            PsiTreeUtil.findChildrenOfType(statement, PyTargetExpression::class.java).any { target ->
                target.qualifier == null && target.name == name
            }
        }
    }

    private fun ensureImportsFromFixture(targetFile: PyFile) {
        val fixtureFile = model.fixtureFunction.containingFile as? PyFile ?: return
        if (fixtureFile.isEquivalentTo(targetFile)) return

        val generator = PyElementGenerator.getInstance(myProject)
        val existingImports = targetFile.importBlock
            .map { it.text }
            .toSet()

        val importStatements = fixtureFile.importBlock
            .filterIsInstance<PyStatement>()
            .filter { it is PyImportStatement || it is PyFromImportStatement }

        if (importStatements.isEmpty()) return

        val anchor = targetFile.importBlock.lastOrNull() ?: targetFile.statements.firstOrNull()

        for (stmt in importStatements) {
            if (stmt.text in existingImports) continue
            val newStmt = generator.createFromText(
                LanguageLevel.forElement(targetFile),
                PyStatement::class.java,
                stmt.text
            )
            if (anchor != null) {
                targetFile.addBefore(newStmt, anchor)
            } else {
                targetFile.add(newStmt)
            }
        }
    }
}
