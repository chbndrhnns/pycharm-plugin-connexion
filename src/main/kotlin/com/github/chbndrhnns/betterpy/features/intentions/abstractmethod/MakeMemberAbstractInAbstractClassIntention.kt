package com.github.chbndrhnns.betterpy.features.intentions.abstractmethod

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.psi.PyImportService
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*

class MakeMemberAbstractInAbstractClassIntention : IntentionAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Make member abstract"

    override fun getFamilyName(): String = "Make member abstract"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableMakeMemberAbstractInAbstractClassIntention) return false
        if (file !is PyFile) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false
        if (AbstractMethodUtils.isAbstractMethod(function)) return false

        val containingClass = function.containingClass ?: return false
        if (!AbstractClassUtils.isAbstractClass(containingClass)) return false

        return function.nameIdentifier != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return
        if (AbstractMethodUtils.isAbstractMethod(function)) return

        val containingClass = function.containingClass ?: return
        if (!AbstractClassUtils.isAbstractClass(containingClass)) return

        MakeMemberAbstractUtils.makeAbstract(function)
    }

    override fun startInWriteAction(): Boolean = true
}

private object AbstractClassUtils {
    fun isAbstractClass(pyClass: PyClass): Boolean {
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

private object MakeMemberAbstractUtils {

    fun makeAbstract(function: PyFunction) {
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

        val generator = PyElementGenerator.getInstance(function.project)
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
        PyImportService().ensureModuleImported(
            file,
            "abc",
            AddImportHelper.ImportPriority.BUILTIN
        )
    }

    private fun ensureFromAbcImportAbstractMethod(file: PyFile) {
        PyImportService().ensureFromImport(file, "abc", "abstractmethod")
    }
}
