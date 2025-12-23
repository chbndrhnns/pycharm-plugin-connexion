package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression

internal fun isDataclassClass(pyClass: PyClass): Boolean {
    // Check for @dataclass decorator (handles aliases via qualified name resolution)
    val decorators = pyClass.decoratorList?.decorators
    if (decorators != null && decorators.any { decorator ->
            // Try to resolve via qualified name (handles aliases)
            val callee = decorator.callee as? PyQualifiedExpression
            val resolvedQName = callee?.asQualifiedName()?.toString()
            if (resolvedQName == "dataclasses.dataclass" || (resolvedQName != null && resolvedQName.endsWith(".dataclass"))) {
                return@any true
            }

            // Fallback: check decorator name and qualifiedName directly (for unresolved cases)
            val name = decorator.name
            val qName = decorator.qualifiedName?.toString()
            name == "dataclass" || qName == "dataclasses.dataclass" || (qName != null && qName.endsWith(".dataclass"))
        }
    ) {
        return true
    }

    // Check for BaseModel base class (handles aliases via reference resolution)
    val superExprs = pyClass.superClassExpressions
    return superExprs.any { superExpr ->
        val ref = superExpr as? PyReferenceExpression

        // Try to resolve the reference to get the actual class
        val resolved = ref?.reference?.resolve()
        if (resolved is PyClass) {
            val qName = resolved.qualifiedName
            if (qName == "pydantic.BaseModel" ||
                qName == "pydantic.main.BaseModel" ||
                (qName != null && qName.endsWith(".BaseModel"))
            ) {
                return@any true
            }
        }

        // Fallback: check name and qualified name directly (for unresolved cases)
        val name = ref?.name
        val qNameOwner = superExpr as? PyQualifiedNameOwner
        val qName = qNameOwner?.qualifiedName
        name == "BaseModel" || (qName != null && qName.endsWith(".BaseModel"))
    }
}
