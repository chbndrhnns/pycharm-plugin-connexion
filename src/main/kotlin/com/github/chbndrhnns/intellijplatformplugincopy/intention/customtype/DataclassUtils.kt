package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression

internal fun isDataclassClass(pyClass: PyClass): Boolean {
    val decorators = pyClass.decoratorList?.decorators
    if (decorators != null && decorators.any { decorator ->
            val name = decorator.name
            val qName = decorator.qualifiedName?.toString()
            name == "dataclass" || qName == "dataclasses.dataclass" || (qName != null && qName.endsWith(".dataclass"))
        }
    ) {
        return true
    }

    val superExprs = pyClass.superClassExpressions
    return superExprs != null && superExprs.any { superExpr ->
        val ref = superExpr as? PyReferenceExpression
        val name = ref?.name
        val qNameOwner = superExpr as? PyQualifiedNameOwner
        val qName = qNameOwner?.qualifiedName
        name == "BaseModel" || (qName != null && qName.endsWith(".BaseModel"))
    }
}
