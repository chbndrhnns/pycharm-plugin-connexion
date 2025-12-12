package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.intellij.psi.PsiParserFacade
import com.intellij.util.ArrayUtilRt
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*

class PytestSkipToggler(private val generator: PyElementGenerator) {

    fun toggleOnFunction(function: PyFunction, file: PyFile) {
        toggleDecorator(function, file)
    }

    fun toggleOnClass(clazz: PyClass, file: PyFile) {
        toggleDecorator(clazz, file)
    }

    fun toggleOnModule(file: PyFile) {
        toggleModuleLevelSkip(file)
    }

    private fun toggleDecorator(element: PyDecoratable, file: PyFile) {
        val skipMarker = "pytest.mark.skip"

        val existingDecorator = element.decoratorList?.decorators?.firstOrNull {
            it.qualifiedName.toString() == skipMarker || it.text.contains(skipMarker)
        }

        if (existingDecorator != null) {
            existingDecorator.delete()
            return
        }

        AddImportHelper.addImportStatement(file, "pytest", null, AddImportHelper.ImportPriority.THIRD_PARTY, null)

        if (element is PyFunction) {
            PyUtil.addDecorator(element, "@$skipMarker")
        } else {
            addDecoratorToDecoratable(element, "@$skipMarker")
        }
    }

    private fun addDecoratorToDecoratable(element: PyDecoratable, decoratorText: String) {
        val currentDecoratorList = element.decoratorList
        val decoTexts = ArrayList<String>()
        decoTexts.add(decoratorText)

        if (currentDecoratorList != null) {
            for (deco in currentDecoratorList.decorators) {
                decoTexts.add(deco.text)
            }
        }

        val newDecoratorList = generator.createDecoratorList(*ArrayUtilRt.toStringArray(decoTexts))

        if (currentDecoratorList != null) {
            currentDecoratorList.replace(newDecoratorList)
        } else {
            element.addBefore(newDecoratorList, element.firstChild)
        }
    }

    private fun toggleModuleLevelSkip(file: PyFile) {
        val assignments = file.statements.filterIsInstance<PyAssignmentStatement>()
        val pytestMarkAssignment = assignments.firstOrNull {
            it.targets.any { target -> target.text == "pytestmark" }
        }

        if (pytestMarkAssignment != null) {
            val value = pytestMarkAssignment.assignedValue

            val items = if (value is PyListLiteralExpression) {
                value.elements.map { it.text }.toMutableList()
            } else if (value != null) {
                mutableListOf(value.text)
            } else {
                mutableListOf()
            }

            val skipMarker = "pytest.mark.skip"
            val hasSkip = items.any { it.contains(skipMarker) }

            if (hasSkip) {
                items.removeIf { it.contains(skipMarker) }
            } else {
                items.add(skipMarker)
            }

            AddImportHelper.addImportStatement(file, "pytest", null, AddImportHelper.ImportPriority.THIRD_PARTY, null)

            val newListText = "[" + items.joinToString(", ") + "]"
            val newAssignment = generator.createFromText(
                LanguageLevel.getLatest(),
                PyAssignmentStatement::class.java,
                "pytestmark = $newListText"
            )
            pytestMarkAssignment.replace(newAssignment)
            return
        }

        AddImportHelper.addImportStatement(file, "pytest", null, AddImportHelper.ImportPriority.THIRD_PARTY, null)

        val newAssignment = generator.createFromText(
            LanguageLevel.getLatest(),
            PyAssignmentStatement::class.java,
            "pytestmark = [pytest.mark.skip]"
        )

        val imports = file.importBlock
        if (imports.isNotEmpty()) {
            val lastImport = imports.last()
            file.addAfter(newAssignment, lastImport)
            file.addAfter(PsiParserFacade.getInstance(file.project).createWhiteSpaceFromText("\n\n"), lastImport)
        } else {
            val firstStatement = file.statements.firstOrNull()
            if (firstStatement != null) {
                file.addBefore(newAssignment, firstStatement)
                file.addBefore(
                    PsiParserFacade.getInstance(file.project).createWhiteSpaceFromText("\n\n"),
                    firstStatement
                )
            } else {
                file.add(newAssignment)
            }
        }
    }
}
