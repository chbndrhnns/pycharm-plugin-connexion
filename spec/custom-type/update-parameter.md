### Updating Type Annotations for Method Parameters in a PyCharm Plugin

To update the type annotation of an instance method parameter and propagate this change to all overriding methods (call sites in the context of inheritance), you generally cannot rely solely on the standard `PyChangeSignatureProcessor` for type changes, as it tends to preserve existing annotations.

Instead, you should perform a direct PSI modification using `PyElementGenerator` and propagate the change using `PyOverridingMethodsSearch`.

#### 1. Modify the Parameter in the Base Method

Use `PyElementGenerator` to create a new parameter with the updated type annotation and replace the existing one.

```java
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;

public void updateParameterType(PyNamedParameter parameter, String newTypeAnnotation) {
    Project project = parameter.getProject();
    PyElementGenerator generator = PyElementGenerator.getInstance(project);
    
    // Create a new parameter with the same name and default value, but new annotation
    // Note: We use the LanguageLevel of the existing element to ensure compatibility
    String defaultValue = parameter.getDefaultValueText();
    PyNamedParameter newParameter = generator.createParameter(
        parameter.getName(),
        defaultValue,
        newTypeAnnotation,
        LanguageLevel.forElement(parameter)
    );
    
    // Replace the old parameter with the new one in the PSI tree
    parameter.replace(newParameter);
}
```

#### 2. Propagate to Overriding Methods

To "update all call sites" in the context of method signatures usually implies updating the hierarchy (overrides). Python is dynamically typed, so call sites (invocations) typically do not need syntax changes unless the *values* passed need to change, which requires complex semantic analysis.

To update overrides:

```java
import com.intellij.util.Query;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.PyFunction;
import java.util.Collection;

public void updateOverrides(PyFunction baseFunction, int paramIndex, String newTypeAnnotation) {
    // Search for all methods that override the base function
    Query<PyFunction> overrides = PyOverridingMethodsSearch.search(baseFunction, true);
    
    for (PyFunction override : overrides.findAll()) {
        PyParameter[] parameters = override.getParameterList().getParameters();
        
        // Check if the override has a parameter at the same index
        if (paramIndex < parameters.length) {
            PyParameter param = parameters[paramIndex];
            
            // Ensure it is a named parameter before updating
            if (param instanceof PyNamedParameter) {
                updateParameterType((PyNamedParameter) param, newTypeAnnotation);
            }
        }
    }
}
```

#### 3. Putting It All Together

Wrap the operation in a `WriteCommandAction` to ensure it runs inside a write transaction.

```java
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.util.PsiTreeUtil;

public void performUpdate(PyNamedParameter targetParameter, String newType) {
    PyFunction function = PsiTreeUtil.getParentOfType(targetParameter, PyFunction.class);
    if (function == null) return;

    int paramIndex = function.getParameterList().getParameterIndex(targetParameter);
    
    WriteCommandAction.runWriteCommandAction(function.getProject(), () -> {
        // 1. Update the base parameter
        updateParameterType(targetParameter, newType);
        
        // 2. Update overrides
        updateOverrides(function, paramIndex, newType);
    });
}
```

### Summary of Tools Used
- **`PyElementGenerator`**: The standard factory for creating Python PSI elements (like parameters with annotations) from text.
- **`PyOverridingMethodsSearch`**: Finds all implementations of the method in subclasses to ensure the signature change is propagated.
- **`PyNamedParameter`**: The PSI interface representing the parameter to be modified.