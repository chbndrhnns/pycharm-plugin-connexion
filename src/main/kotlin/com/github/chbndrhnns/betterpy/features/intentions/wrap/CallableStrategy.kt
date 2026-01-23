package com.github.chbndrhnns.betterpy.features.intentions.wrap

import com.github.chbndrhnns.betterpy.features.intentions.LambdaWrap
import com.github.chbndrhnns.betterpy.features.intentions.shared.PyTypeIntentions
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType

class CallableStrategy : WrapStrategy {
    override fun run(context: AnalysisContext): StrategyResult {
        val expectedCallable = PyTypeIntentions.getExpectedCallableType(context.element, context.typeEval)
            ?: return StrategyResult.Continue

        // Skip class constructors (e.g. str, int) unless it's a typing.Callable alias
        // (though typing.Callable usually isn't isDefinition=true in this context).
        if (expectedCallable is PyClassType && expectedCallable.isDefinition) {
            return StrategyResult.Continue
        }

        if (!expectedCallable.isCallable) {
            return StrategyResult.Continue
        }

        // Check for return type mismatch: if Callable expects None, but we are wrapping a non-None value.
        val returnType = expectedCallable.getReturnType(context.typeEval)
        if (isNoneType(returnType)) {
            val actualType = context.typeEval.getType(context.element)
            // If actual type is known and not None, we shouldn't wrap it into a lambda that returns a value.
            if (actualType != null && !isNoneType(actualType)) {
                return StrategyResult.Continue
            }
        }

        val paramList = expectedCallable.getParameters(context.typeEval)
        val paramString = if (paramList != null) {
            if (paramList.isEmpty()) ""
            else {
                paramList.mapIndexed { index, param ->
                    param.name ?: if (paramList.size == 1) "_" else "p${index + 1}"
                }.joinToString(", ")
            }
        } else {
            "*args"
        }

        return StrategyResult.Found(LambdaWrap(context.element, paramString))
    }

    private fun isNoneType(type: PyType?): Boolean {
        if (type == null) return false
        if (type.name == "None" || type.name == "NoneType") return true

        val asClass = type as? PyClassType ?: return false
        val qName = asClass.classQName ?: return false
        return qName.endsWith("NoneType") ||
                qName.equals("None", true) ||
                qName.equals("builtins.None", true) ||
                qName.equals("builtins.NoneType", true)
    }
}
