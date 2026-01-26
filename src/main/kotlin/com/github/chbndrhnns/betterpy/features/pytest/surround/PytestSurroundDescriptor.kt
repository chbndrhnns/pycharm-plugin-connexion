package com.github.chbndrhnns.betterpy.features.pytest.surround

import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.PyStatementList

class PytestSurroundDescriptor : SurroundDescriptor {

    private val surrounders = arrayOf<Surrounder>(PytestRaisesSurrounder())

    override fun getSurrounders(): Array<Surrounder> = surrounders
    override fun isExclusive(): Boolean = false

    override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<PsiElement> {
        val pyFile = file as? PyFile ?: return emptyArray()
        val startElement = pyFile.findElementAt(startOffset) ?: return emptyArray()
        val endElement = pyFile.findElementAt((endOffset - 1).coerceAtLeast(startOffset)) ?: return emptyArray()

        val statementList =
            PsiTreeUtil.getParentOfType(startElement, PyStatementList::class.java, false) ?: return emptyArray()
        if (PsiTreeUtil.getParentOfType(endElement, PyStatementList::class.java, false) != statementList) {
            return emptyArray()
        }

        if (startOffset == endOffset) {
            val statement = PsiTreeUtil.getParentOfType(startElement, PyStatement::class.java, false)
                ?: return emptyArray()
            if (statement.parent != statementList) return emptyArray()
            return arrayOf(statement)
        }

        val statements = statementList.statements.filter { statement ->
            val range = statement.textRange
            range.startOffset >= startOffset && range.endOffset <= endOffset
        }

        return statements.toTypedArray()
    }
}
