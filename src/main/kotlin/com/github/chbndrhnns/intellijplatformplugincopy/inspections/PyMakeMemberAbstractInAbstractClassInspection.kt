package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.intention.abstractmethod.AbstractMethodUtils
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.*

class PyMakeMemberAbstractInAbstractClassInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession,
    ): PsiElementVisitor {
        if (!PluginSettingsState.instance().state.enableMakeMemberAbstractInAbstractClassInspection) {
            return object : PyElementVisitor() {}
        }
        return Visitor(holder)
    }

    private class Visitor(
        private val holder: ProblemsHolder,
    ) : PyElementVisitor() {

        override fun visitPyClass(node: PyClass) {
            super.visitPyClass(node)

            if (!isAbstractClass(node)) return

            for (method in node.methods) {
                if (AbstractMethodUtils.isAbstractMethod(method)) continue
                val nameIdentifier = method.nameIdentifier ?: continue
                holder.registerProblem(nameIdentifier, DESCRIPTION, MakeMemberAbstractFix(method))
            }
        }
    }

    class MakeMemberAbstractFix(function: PyFunction) : LocalQuickFix {

        private val functionPointer: SmartPsiElementPointer<PyFunction> =
            SmartPointerManager.getInstance(function.project).createSmartPsiElementPointer(function)

        override fun getFamilyName(): String = FIX_NAME

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val function = functionPointer.element ?: return
            if (AbstractMethodUtils.isAbstractMethod(function)) return

            val file = function.containingFile as? PyFile ?: return

            val importStyle = determineImportStyle(file)
            val decoratorText = when (importStyle) {
                ImportStyle.IMPORT_ABC -> "@abc.abstractmethod"
                ImportStyle.FROM_ABC_IMPORT -> "@abstractmethod"
            }

            val existing = function.decoratorList?.decorators?.map { it.text } ?: emptyList()
            if (existing.any { it.contains("abstractmethod") }) return

            when (importStyle) {
                ImportStyle.IMPORT_ABC -> ensureImportAbc(file)
                ImportStyle.FROM_ABC_IMPORT -> ensureFromAbcImportAbstractMethod(file)
            }

            val insertionIndex = findAbstractDecoratorInsertionIndex(existing)

            val newDecos = ArrayList<String>(existing.size + 1)
            newDecos.addAll(existing.take(insertionIndex))
            newDecos.add(decoratorText)
            newDecos.addAll(existing.drop(insertionIndex))

            val generator = PyElementGenerator.getInstance(project)
            val newDecoratorList = generator.createDecoratorList(*newDecos.toTypedArray())

            val currentDecoratorList = function.decoratorList
            if (currentDecoratorList != null) {
                currentDecoratorList.replace(newDecoratorList)
            } else {
                function.addBefore(newDecoratorList, function.firstChild)
            }
        }

        private fun findAbstractDecoratorInsertionIndex(existingDecoratorTexts: List<String>): Int {
            if (existingDecoratorTexts.isEmpty()) return 0

            val first = existingDecoratorTexts.first().removePrefix("@").trim()
            val isPropertyRelated = first == "property" || first.endsWith(".setter") || first.endsWith(".deleter")
            return if (isPropertyRelated) 1 else 0
        }

        private enum class ImportStyle {
            IMPORT_ABC,
            FROM_ABC_IMPORT,
        }

        private fun determineImportStyle(file: PyFile): ImportStyle {
            // If either form is already present, reuse it. If both are present, prefer `import abc`
            // because it avoids modifying existing `from ... import ...` lists.
            if (hasImportAbc(file)) return ImportStyle.IMPORT_ABC
            if (hasFromAbcImport(file)) return ImportStyle.FROM_ABC_IMPORT
            return ImportStyle.IMPORT_ABC
        }

        private fun hasImportAbc(file: PyFile): Boolean {
            return file.statements
                .filterIsInstance<PyImportStatement>()
                .flatMap { it.importElements.toList() }
                .any { it.importedQName?.toString() == "abc" || it.visibleName == "abc" }
        }

        private fun hasFromAbcImport(file: PyFile): Boolean {
            return file.statements
                .filterIsInstance<PyFromImportStatement>()
                .any { it.importSourceQName?.toString() == "abc" }
        }

        private fun ensureImportAbc(file: PyFile) {
            if (hasImportAbc(file)) return
            AddImportHelper.addImportStatement(
                file,
                "abc",
                null,
                AddImportHelper.ImportPriority.BUILTIN,
                null,
            )
        }

        private fun ensureFromAbcImportAbstractMethod(file: PyFile) {
            // Try to extend an existing `from abc import ...` statement.
            val fromAbc = file.statements
                .filterIsInstance<PyFromImportStatement>()
                .firstOrNull { it.importSourceQName?.toString() == "abc" }

            if (fromAbc != null) {
                val importedNames = fromAbc.importElements.mapNotNull { it.importedQName?.lastComponent }
                if (!importedNames.contains("abstractmethod")) {
                    val generator = PyElementGenerator.getInstance(file.project)
                    val languageLevel = LanguageLevel.forElement(file)
                    val newImport = generator.createFromText(
                        languageLevel,
                        PyFromImportStatement::class.java,
                        "from abc import abstractmethod",
                    )
                    val newElement = newImport.importElements.firstOrNull()
                    if (newElement != null) {
                        val last = fromAbc.importElements.lastOrNull()
                        if (last != null) {
                            fromAbc.addAfter(newElement, last)
                        } else {
                            fromAbc.add(newElement)
                        }
                    }
                }
                return
            }

            // No existing `from abc import ...` – create it.
            val generator = PyElementGenerator.getInstance(file.project)
            val languageLevel = LanguageLevel.forElement(file)
            val newStmt = generator.createFromText(
                languageLevel,
                PyFromImportStatement::class.java,
                "from abc import abstractmethod",
            )
            // Place it near other imports if possible.
            val anchor = PsiTreeUtil.findChildrenOfType(file, PyImportStatementBase::class.java)
                .firstOrNull()
            if (anchor != null) {
                file.addBefore(newStmt, anchor)
            } else {
                file.addBefore(newStmt, file.firstChild)
            }
        }
    }

    companion object {
        private const val DESCRIPTION = "Member in abstract class should be abstract"
        private const val FIX_NAME = "Make member abstract"

        private fun isAbstractClass(pyClass: PyClass): Boolean {
            // Base class detection: class C(abc.ABC) / class C(ABC)
            val superClassExpressions = pyClass.superClassExpressions
            val isAbcBase = superClassExpressions.any { expr ->
                val text = expr.text
                text == "abc.ABC" || text == "ABC"
            }

            if (isAbcBase) return true

            // Metaclass detection: class C(metaclass=abc.ABCMeta) / class C(metaclass=ABCMeta)
            val superClassExpressionList = pyClass.superClassExpressionList
            val args = superClassExpressionList?.arguments ?: emptyArray()
            val metaArg = args.filterIsInstance<PyKeywordArgument>().firstOrNull { it.keyword == "metaclass" }
            val metaExpr = metaArg?.valueExpression
            val metaText = metaExpr?.text
            return metaText == "abc.ABCMeta" || metaText == "ABCMeta"
        }
    }
}
