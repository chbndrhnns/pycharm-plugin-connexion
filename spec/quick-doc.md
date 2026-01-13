### Analysis

To render quick infos (hover documentation) for Dataclasses and Pydantic models, we can leverage the `PythonDocumentationQuickInfoProvider` extension point. This API allows injecting custom HTML content into the quick documentation popup when hovering over an element.

The key challenges are:
1.  **Detection**: Reliably identifying Dataclasses and Pydantic models.
    *   **Dataclasses**: The `com.jetbrains.python.codeInsight.PyDataclasses` utility provides robust methods like `parseDataclassParameters` to detect dataclasses and their variants (including `attrs` and `dataclass_transform`).
    *   **Pydantic**: Since Pydantic models inherit from `pydantic.BaseModel`, checking for this inheritance via `PyClass.isSubclass` is the standard approach.
2.  **Field Retrieval**: We need to list fields including those from base classes. `PyClass.getClassAttributesInherited(TypeEvalContext)` is the most suitable existing API, as it returns class attributes from the class and its ancestors. We need to filter these to ensure they are relevant fields (e.g., have type annotations).
3.  **Formatting**: The result needs to be an HTML string.

### Plan

1.  **Implement `PythonDocumentationQuickInfoProvider`**: Create a class `PyDataclassAndPydanticQuickInfoProvider`.
2.  **Implement `getQuickInfo`**:
    *   Resolve the target element from `originalElement`.
    *   Check if it is a `PyClass`.
    *   Use `PyDataclasses.parseDataclassParameters` to check for Dataclasses.
    *   Use `cls.isSubclass("pydantic.BaseModel", context)` (and check for `pydantic.main.BaseModel` for V2 compatibility) to check for Pydantic models.
3.  **Collect and Filter Fields**:
    *   If identified, call `cls.getClassAttributesInherited(context)`.
    *   Filter attributes to keep only those that are likely fields (e.g., have annotations, are not private/internal if desired).
    *   Deduplicate fields by name if necessary (keeping the most derived version).
4.  **Render HTML**:
    *   Construct an HTML table or list showing `Field Name: Type`.
    *   Use `TypeEvalContext` to resolve and format types.

### Implementation

```java
package com.myplugin.documentation;

import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyDataclasses;
import com.jetbrains.python.documentation.PythonDocumentationQuickInfoProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PyDataclassAndPydanticQuickInfoProvider implements PythonDocumentationQuickInfoProvider {

  @Override
  public @Nullable String getQuickInfo(@NotNull PsiElement originalElement) {
    PsiElement element = originalElement.getParent(); // Usually resolving from the identifier
    // Depending on context, you might need to resolve the reference:
    // if (originalElement.getParent() instanceof PyReferenceExpression ref) { element = ref.resolve(); }
    
    // For the definition itself (e.g. hovering over the class name in definition):
    if (!(element instanceof PyClass)) {
        // If we are at usage, we might need to resolve
        // This logic depends on exactly where the user hovers (usage vs definition)
        // Assuming we have access to the PyClass:
        return null;
    }
    
    PyClass cls = (PyClass) element;
    TypeEvalContext context = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile());

    boolean isDataclass = PyDataclasses.parseDataclassParameters(cls, context) != null;
    boolean isPydantic = !isDataclass && (cls.isSubclass("pydantic.BaseModel", context) || cls.isSubclass("pydantic.main.BaseModel", context));

    if (!isDataclass && !isPydantic) {
      return null;
    }

    List<PyTargetExpression> attributes = cls.getClassAttributesInherited(context);
    // Use a map to keep insertion order (if meaningful) and deduplicate by name
    Map<String, PyTargetExpression> fields = new LinkedHashMap<>();

    // Note: attributes are usually returned from derived to base or vice-versa. 
    // We want the most specific version.
    for (PyTargetExpression attr : attributes) {
      String name = attr.getName();
      if (name != null && !name.startsWith("_") && attr.getAnnotationValue() != null) {
          // If we encounter a field seen before, it's either an override or shadow.
          // Depending on iteration order of getClassAttributesInherited, handle accordingly.
          fields.putIfAbsent(name, attr);
      }
    }

    if (fields.isEmpty()) {
      return null;
    }

    HtmlBuilder builder = new HtmlBuilder();
    builder.append(HtmlChunk.tag("h3").addText(isPydantic ? "Pydantic Model Fields" : "Dataclass Fields"));
    
    HtmlChunk.Element table = HtmlChunk.tag("table").attr("border", "0").attr("cellpadding", "2").attr("cellspacing", "0");
    
    for (Map.Entry<String, PyTargetExpression> entry : fields.entrySet()) {
      String name = entry.getKey();
      PyTargetExpression attr = entry.getValue();
      
      String typeName = context.getType(attr) != null ? context.getType(attr).getName() : "Any";
      
      HtmlChunk.Element row = HtmlChunk.tag("tr");
      row.child(HtmlChunk.tag("td").style("color: #888; padding-right: 10px;").addText(name));
      row.child(HtmlChunk.tag("td").addText(typeName));
      table.child(row);
    }
    
    builder.append(table);
    return builder.toString();
  }
}
```

### Test Cases

To verify this, we need a test that sets up the Python environment with `pydantic` and `dataclasses` mock definitions and asserts that the provider returns the expected HTML.

```java
package com.myplugin.documentation;

import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.python.psi.PyClass;

public class PyDataclassAndPydanticQuickInfoTest extends BasePlatformTestCase {

  public void testDataclassFields() {
    myFixture.configureByText("test.py", """
      import dataclasses
      
      @dataclasses.dataclass
      class Base:
          base_field: int
          
      @dataclasses.dataclass
      class Derived(Base):
          derived_field: str
    """);

    PsiElement element = myFixture.findElementByText("Derived", PyClass.class);
    PyDataclassAndPydanticQuickInfoProvider provider = new PyDataclassAndPydanticQuickInfoProvider();
    String info = provider.getQuickInfo(element.getNavigationElement()); // simulate hover on name

    assertNotNull(info);
    assertTrue(info.contains("Dataclass Fields"));
    assertTrue(info.contains("base_field"));
    assertTrue(info.contains("int"));
    assertTrue(info.contains("derived_field"));
    assertTrue(info.contains("str"));
  }

  public void testPydanticFields() {
    // Mock pydantic structure
    myFixture.configureByText("pydantic.py", """
      class BaseModel:
          pass
    """);
    
    myFixture.configureByText("test_pydantic.py", """
      from pydantic import BaseModel
      
      class User(BaseModel):
          id: int
          name: str
    """);

    PsiElement element = myFixture.findElementByText("User", PyClass.class);
    PyDataclassAndPydanticQuickInfoProvider provider = new PyDataclassAndPydanticQuickInfoProvider();
    String info = provider.getQuickInfo(element.getNavigationElement());

    assertNotNull(info);
    assertTrue(info.contains("Pydantic Model Fields"));
    assertTrue(info.contains("id"));
    assertTrue(info.contains("int"));
    assertTrue(info.contains("name"));
    assertTrue(info.contains("str"));
  }
}
```