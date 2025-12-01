### Issue Analysis

The error `java.lang.IllegalStateException: This method is forbidden on EDT because it does not pump the event queue` occurs because `ReferencesSearch.search(...).findAll()` is being executed on the Event Dispatch Thread (EDT) inside a Write Action.

The intention action `IntroduceCustomTypeFromStdlibIntention` has `startInWriteAction() = true`, which forces the entire execution (including the potentially expensive reference search) to run in a Write Action on the EDT. The underlying Python reference resolution logic (`PyNamedParameterImpl.getReferences`) triggers a check that forbids this context to prevent UI freezes and deadlocks.

### Solution

To fix this, we need to:
1.  **Run the intention without an initial Write Action** so we can control threading.
2.  **Separate the "Search" phase from the "Modification" phase**.
3.  **Execute the search on a background thread** (using `runWithModalProgressBlocking` as suggested by the error) to satisfy the platform's threading requirements.
4.  **Execute the modification in a Write Action** after the search is complete.

Here are the required changes:

#### 1. `UsageRewriter.kt`

Split `wrapFunctionUsages` into two methods: `findUsagesToWrap` (search) and `wrapUsages` (modification). We use `SmartPsiElementPointer` to safely pass found elements between the read/search phase and the write phase.

```kotlin
// ... existing imports ...
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer

class UsageRewriter {

    // ... existing methods (rewriteAnnotation, wrapExpression, etc.) ...

    /**
     * PHASE 1: Search for usages to wrap. Safe to run in Read Action / Background Thread.
     */
    fun findUsagesToWrap(
        function: PyFunction,
        parameter: PyNamedParameter
    ): List<SmartPsiElementPointer<PyExpression>> {
        val paramName = parameter.name ?: return emptyList()
        val scope = GlobalSearchScope.fileScope(function.containingFile)
        val references = ReferencesSearch.search(function, scope).findAll()

        val resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(function.project))
        val pointerManager = SmartPointerManager.getInstance(function.project)
        val results = mutableListOf<SmartPsiElementPointer<PyExpression>>()

        for (ref in references) {
            val element = ref.element
            val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java) ?: continue

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
                            results.add(pointerManager.createSmartPsiElementPointer(realArg))
                        }
                    }
                }
            }
        }
        return results
    }

    /**
     * PHASE 2: Modify the found usages. Must run in Write Action.
     */
    fun wrapUsages(
        usages: List<SmartPsiElementPointer<PyExpression>>,
        wrapperTypeName: String,
        generator: PyElementGenerator
    ) {
        for (ptr in usages) {
            val element = ptr.element ?: continue
            wrapArgumentExpressionIfNeeded(element, wrapperTypeName, generator)
        }
    }

    // Remove or deprecate the old wrapFunctionUsages method
    // ...
}
```

#### 2. `CustomTypeApplier.kt`

Refactor `apply` to orchestrate the search and write phases. Use `runWithModalProgressBlocking` to offload the search when running on the EDT.

```kotlin
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.runWithModalProgressBlocking
// ... other imports

class CustomTypeApplier(...) {

    fun apply(project: Project, editor: Editor, plan: CustomTypePlan, isPreview: Boolean = false) {
        val pyFile = plan.sourceFile
        val builtinName = plan.builtinName
        
        // --- PHASE 1: Analysis & Search (Read / BGT) ---
        
        // Identify if we are acting on a parameter and need to update call sites
        val paramToUpdate: Pair<PyFunction, PyNamedParameter>? = if (plan.annotationRef != null) {
            val annRef = plan.annotationRef
            val parameter = PsiTreeUtil.getParentOfType(annRef, PyNamedParameter::class.java)
            if (parameter != null && parameter.name != null) {
                val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java)
                if (function != null) function to parameter else null
            } else null
        } else null

        val usagesToWrap = if (paramToUpdate != null) {
            val (function, parameter) = paramToUpdate
            
            // If on EDT (and not in preview/test), we must move to BGT for search
            if (!isPreview && ApplicationManager.getApplication().isDispatchThread) {
                runWithModalProgressBlocking(project, "Finding usages to update...") {
                    readAction {
                        rewriter.findUsagesToWrap(function, parameter)
                    }
                }
            } else {
                // Already in BGT or allowed context
                rewriter.findUsagesToWrap(function, parameter)
            }
        } else emptyList()


        // --- PHASE 2: Execution (Write Action) ---
        
        val executionBlock = {
            val builtinForClass = generator.determineBaseClassText(builtinName, plan.annotationRef)
            val baseTypeName = naming.suggestTypeName(builtinName, plan.preferredClassName)
            val pyGenerator = PyElementGenerator.getInstance(project)

            val targetFileForNewClass = insertionPointFinder.chooseFile(plan.field, plan.expression, pyFile)
            val newTypeName = naming.ensureUnique(targetFileForNewClass, baseTypeName)
            
            // 1. Insert Class
            val newClass = generator.insertClass(
                targetFileForNewClass,
                generator.createClass(project, newTypeName, builtinForClass),
            )

            if (targetFileForNewClass != pyFile) {
                val anchorElement = plan.expression ?: plan.annotationRef
                if (anchorElement != null) imports.ensureImportedIfNeeded(pyFile, anchorElement, newClass)
            }

            val newRefBase = pyGenerator.createExpressionFromText(LanguageLevel.getLatest(), newTypeName)

            // 2. Rewrite Annotation & Expression
            if (plan.annotationRef != null) {
                val annRef = plan.annotationRef
                val assignedExpr = plan.assignedExpression

                if (assignedExpr != null) {
                    rewriter.rewriteAnnotation(annRef, newRefBase, builtinName)
                    if (shouldWrapAssignedExpression(assignedExpr, builtinName)) {
                        rewriter.wrapExpression(assignedExpr, newTypeName, pyGenerator)
                    }
                } else {
                    rewriter.rewriteAnnotation(annRef, newRefBase, builtinName)
                }
                
                // 3. Wrap Call Site Usages (using found pointers)
                if (usagesToWrap.isNotEmpty()) {
                    rewriter.wrapUsages(usagesToWrap, newTypeName, pyGenerator)
                }
            }

            // ... (rest of logic for expression / dataclass fields unchanged) ...
             if (plan.expression != null) {
                val originalExpr = plan.expression
                rewriter.updateParameterAnnotationFromCallSite(originalExpr, builtinName, newTypeName, pyGenerator)
                rewriter.wrapExpression(originalExpr, newTypeName, pyGenerator)
            }
            
            // ...
            
            if (!isPreview && targetFileForNewClass == pyFile && plan.preferredClassName == null) {
                startInlineRename(project, editor, newClass, pyFile)
            }
        }

        if (isPreview) {
            ApplicationManager.getApplication().runWriteAction(executionBlock)
        } else {
            WriteCommandAction.runWriteCommandAction(project, executionBlock)
        }
    }
}
```

#### 3. `IntroduceCustomTypeFromStdlibIntention.kt`

Disable the automatic write action start.

```kotlin
class IntroduceCustomTypeFromStdlibIntention : ... {
    // ... 

    // Change this to FALSE
    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val plan = getPlan(editor, file) ?: return
        // Now invokes apply(), which handles its own WriteAction management
        applier.apply(project, editor, plan)
    }
    
    // ...
}
```