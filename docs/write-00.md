### Solution to fix `testParameterAnnotationUpdateWrapsCallSites`

The test `testParameterAnnotationUpdateWrapsCallSites` fails because the intention "Introduce custom type from ..." currently updates the parameter annotation but **does not update the call sites** where arguments are passed to that parameter.

To fix this, we need to implement logic to find usages of the function and wrap the corresponding arguments with the new custom type constructor.

#### 1. Modify `UsageRewriter.kt`

Add a new method `wrapFunctionUsages` to `UsageRewriter` that finds usages of a function and wraps the argument corresponding to the modified parameter.

You will need to add these imports:
```kotlin
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
```

Add the method to `UsageRewriter` class:

```kotlin
    /**
     * Wraps arguments at call sites when a function parameter type is updated.
     */
    fun wrapFunctionUsages(
        function: PyFunction,
        parameter: PyNamedParameter,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ) {
        val paramName = parameter.name ?: return
        // Limit search to the containing file for now (consistent with existing logic guarantees)
        // For project-wide support, usage scope could be expanded.
        val scope = GlobalSearchScope.fileScope(function.containingFile)
        val references = ReferencesSearch.search(function, scope).findAll()

        val resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(function.project))

        for (ref in references) {
            val element = ref.element
            val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: continue
            
            // Ensure we are looking at the function call
            if (call.callee?.reference?.resolve() != function) continue

            val mappingList = call.multiMapArguments(resolveContext)
            for (mapping in mappingList) {
                for ((arg, paramWrapper) in mapping.mappedParameters) {
                    if (paramWrapper.name == paramName) {
                        val realArg = when (arg) {
                            is PyKeywordArgument -> arg.valueExpression
                            is PyStarArgument -> null
                            else -> arg
                        }
                        if (realArg != null) {
                            wrapArgumentExpressionIfNeeded(realArg, wrapperTypeName, generator)
                        }
                    }
                }
            }
        }
    }
```

#### 2. Modify `CustomTypeApplier.kt`

Update the `apply` method in `CustomTypeApplier` to invoke `wrapFunctionUsages` when the intention is applied to a parameter annotation.

Locate the `if (plan.annotationRef != null)` block (around line 75) and add the check for `PyNamedParameter`:

```kotlin
        if (plan.annotationRef != null) {
            val annRef = plan.annotationRef

            // ... existing code for assignments ...

            // Existing annotation rewrite
            rewriter.rewriteAnnotation(annRef, newRefBase, builtinName)
            
            // --- INSERT THIS BLOCK ---
            // If we updated a function parameter, we must also wrap the arguments at call sites.
            val parameter = PsiTreeUtil.getParentOfType(annRef, PyNamedParameter::class.java)
            if (parameter != null) {
                val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java)
                if (function != null) {
                    rewriter.wrapFunctionUsages(function, parameter, newTypeName, pyGenerator)
                }
            }
            // -------------------------

            val assignedExpr = plan.assignedExpression
            // ... rest of the existing code
        }
```

With these changes, when the intention is run on `val: str`, it will not only change it to `val: Customstr` but also find `self.do(str("abc"))` and wrap it to `self.do(Customstr(str("abc")))`, making the test pass.