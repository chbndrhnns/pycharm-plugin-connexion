package com.github.chbndrhnns.betterpy.core.python

import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.types.TypeEvalContext
import java.lang.reflect.Method

object PyProtocolUtil {

    private val sdkIsProtocolMethods: List<Method> by lazy {
        // REFACTOR: For compatibility with 261 builds, check other options!
        runCatching {
            val cls = Class.forName("com.jetbrains.python.codeInsight.typing.PyProtocolsKt")
            cls.methods.filter { it.name == "isProtocol" && it.parameterTypes.size == 2 }
        }.getOrDefault(emptyList())
    }

    fun isProtocol(pyClass: PyClass, context: TypeEvalContext): Boolean {
        val sdkResult = callSdkIsProtocol(pyClass, context)
        if (sdkResult == true) return true

        return pyClass.getAncestorClasses(context).any {
            it.name == "Protocol" ||
                    it.qualifiedName == "typing.Protocol" ||
                    it.qualifiedName == "typing_extensions.Protocol"
        }
    }

    private fun callSdkIsProtocol(pyClass: PyClass, context: TypeEvalContext): Boolean? {
        if (sdkIsProtocolMethods.isEmpty()) return null

        val classType = context.getType(pyClass)

        for (method in sdkIsProtocolMethods) {
            val params = method.parameterTypes
            val arg0 = when {
                params[0].isInstance(pyClass) -> pyClass
                classType != null && params[0].isInstance(classType) -> classType
                else -> null
            } ?: continue

            if (!params[1].isInstance(context)) continue

            val result = runCatching { method.invoke(null, arg0, context) }.getOrNull()
            if (result is Boolean) return result
        }

        return null
    }
}
