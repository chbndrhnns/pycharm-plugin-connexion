### Analysis: Rendering Quick Info for Dataclasses and Pydantic Models in PyCharm

Based on the IntelliJ IDEA/PyCharm codebase analysis, here's a comprehensive plan for creating an external plugin that renders enhanced quick info for dataclasses and Pydantic models, displaying field names and types including inheritance.

---

### Architecture Overview

PyCharm provides several extension points for customizing documentation and quick info display:

#### Key Extension Points

1. **`PythonDocumentationQuickInfoProvider`** (Primary)
   - **Location**: `com.jetbrains.python.documentation.PythonDocumentationQuickInfoProvider`
   - **Extension Point**: `Pythonid.pythonDocumentationQuickInfoProvider`
   - **Purpose**: Inject custom quick info into the documentation provider
   - **Methods**:
     - `getQuickInfo(PsiElement originalElement): String` - Returns HTML for quick info
     - `getHoverAdditionalQuickInfo(TypeEvalContext, PsiElement): String` - Experimental hover info

2. **`PyDataclassParametersProvider`** (For Pydantic Support)
   - **Location**: `com.jetbrains.python.codeInsight.PyDataclassParametersProvider`
   - **Extension Point**: `Pythonid.pyDataclassParametersProvider`
   - **Purpose**: Register custom dataclass-like decorators (e.g., Pydantic's `@pydantic.dataclass` or `BaseModel`)
   - **Methods**:
     - `getType(): Type` - Return custom type identifier
     - `getDecoratorAndTypeAndParameters(Project): Triple<QualifiedName, Type, List<PyCallableParameter>>?`
     - `getDataclassParameters(PyClass, TypeEvalContext): PyDataclassParameters?`

3. **`PyClassMembersProvider`** (Optional)
   - **Extension Point**: `Pythonid.pyClassMembersProvider`
   - **Purpose**: Add synthetic members to classes (like `__slots__`, `__attrs_attrs__`)

---

### Existing Infrastructure to Reuse

#### 1. Dataclass Detection and Field Collection

```kotlin
// Use existing utilities from PyDataclasses.kt
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.PyDataclassParameters

// Check if class is a dataclass
val dataclassParams = parseDataclassParameters(pyClass, context)
if (dataclassParams != null) {
    // It's a dataclass (standard, attrs, or dataclass_transform)
    val type = dataclassParams.type // STD, ATTRS, or DATACLASS_TRANSFORM
}

// Get class attributes (fields)
val fields = pyClass.classAttributes // List<PyTargetExpression>
val inheritedFields = pyClass.getClassAttributesInherited(context)
```

#### 2. Styling and HTML Rendering

```kotlin
// Use existing styling utilities from PyDocSignaturesHighlighter.kt
import com.jetbrains.python.documentation.styledSpan
import com.jetbrains.python.documentation.highlightExpressionText
import com.jetbrains.python.highlighting.PyHighlighter

// Create styled HTML
val html = HtmlBuilder()
    .append(styledSpan("class ", PyHighlighter.PY_KEYWORD))
    .append(styledSpan(className, PyHighlighter.PY_CLASS_DEFINITION))
    .br()
    .append(styledSpan("field_name", PyHighlighter.PY_PARAMETER))
    .append(styledSpan(": ", PyHighlighter.PY_OPERATION_SIGN))
    .append(styledSpan(typeName, PyHighlighter.PY_ANNOTATION))
```

#### 3. Type Information

```kotlin
// Use PythonDocumentationProvider utilities
import com.jetbrains.python.documentation.PythonDocumentationProvider

// Get type name for display
val typeName = PythonDocumentationProvider.getTypeName(type, context)
val typeHint = PythonDocumentationProvider.getTypeHint(type, context)

// Format type with links
val typeChunk = PythonDocumentationProvider.formatTypeWithLinks(type, typeOwner, anchor, context)
```

---

### Implementation Plan

#### Phase 1: Basic Quick Info Provider for Dataclasses

**File**: `DataclassQuickInfoProvider.kt`

```kotlin
package com.example.pycharm.dataclass

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.psi.PsiElement
import com.jetbrains.python.documentation.PythonDocumentationQuickInfoProvider
import com.jetbrains.python.documentation.styledSpan
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.codeInsight.parseDataclassParameters
import org.jetbrains.annotations.Nls

class DataclassQuickInfoProvider : PythonDocumentationQuickInfoProvider {
    
    override fun getQuickInfo(originalElement: PsiElement): String? {
        // Get the referenced class
        val pyClass = when (originalElement) {
            is PyReferenceExpression -> originalElement.reference.resolve() as? PyClass
            is PyClass -> originalElement
            else -> null
        } ?: return null
        
        // Check if it's a dataclass
        val context = TypeEvalContext.userInitiated(
            pyClass.project, 
            pyClass.containingFile
        )
        val dataclassParams = parseDataclassParameters(pyClass, context) ?: return null
        
        // Build enhanced quick info
        return buildDataclassQuickInfo(pyClass, dataclassParams, context)
    }
    
    private fun buildDataclassQuickInfo(
        pyClass: PyClass,
        params: com.jetbrains.python.codeInsight.PyDataclassParameters,
        context: TypeEvalContext
    ): String {
        val html = HtmlBuilder()
        
        // Class header (reuse existing styling)
        html.append(styledSpan("@dataclass", PyHighlighter.PY_DECORATOR))
        html.br()
        html.append(styledSpan("class ", PyHighlighter.PY_KEYWORD))
        html.append(styledSpan(pyClass.name ?: "Unknown", PyHighlighter.PY_CLASS_DEFINITION))
        
        // Add superclasses if any
        val superClasses = pyClass.superClassExpressions
        if (superClasses.isNotEmpty()) {
            html.append(styledSpan("(", PyHighlighter.PY_PARENTHS))
            html.append(superClasses.joinToString(", ") { it.text })
            html.append(styledSpan(")", PyHighlighter.PY_PARENTHS))
        }
        html.append(styledSpan(":", PyHighlighter.PY_OPERATION_SIGN))
        html.br()
        
        // Add fields section
        html.br()
        html.append(styledSpan("Fields:", PyHighlighter.PY_KEYWORD))
        html.br()
        
        // Collect fields with inheritance
        val fields = collectFieldsWithInheritance(pyClass, context)
        
        if (fields.isEmpty()) {
            html.append("  (no fields)")
        } else {
            fields.forEach { (field, sourceClass) ->
                html.append("  ")
                
                // Field name
                html.append(styledSpan(field.name ?: "unknown", PyHighlighter.PY_PARAMETER))
                
                // Type annotation
                val fieldType = context.getType(field)
                if (fieldType != null) {
                    html.append(styledSpan(": ", PyHighlighter.PY_OPERATION_SIGN))
                    val typeName = PythonDocumentationProvider.getTypeName(fieldType, context)
                    html.append(styledSpan(typeName, PyHighlighter.PY_ANNOTATION))
                }
                
                // Default value
                val defaultValue = field.findAssignedValue()
                if (defaultValue != null) {
                    html.append(styledSpan(" = ", PyHighlighter.PY_OPERATION_SIGN))
                    val valueText = defaultValue.text
                    if (valueText.length > 30) {
                        html.append("...")
                    } else {
                        html.append(valueText)
                    }
                }
                
                // Show inheritance source
                if (sourceClass != pyClass) {
                    html.append(styledSpan(" (from ${sourceClass.name})", PyHighlighter.PY_COMMENT))
                }
                
                html.br()
            }
        }
        
        // Add dataclass parameters info
        html.br()
        html.append(buildDataclassParamsInfo(params))
        
        return html.toString()
    }
    
    private fun collectFieldsWithInheritance(
        pyClass: PyClass,
        context: TypeEvalContext
    ): List<Pair<com.jetbrains.python.psi.PyTargetExpression, PyClass>> {
        val result = mutableListOf<Pair<com.jetbrains.python.psi.PyTargetExpression, PyClass>>()
        val seenNames = mutableSetOf<String>()
        
        // Collect from current class and ancestors
        val classesToCheck = mutableListOf(pyClass)
        classesToCheck.addAll(pyClass.getAncestorClasses(context))
        
        for (cls in classesToCheck) {
            // Check if ancestor is also a dataclass
            val ancestorParams = parseDataclassParameters(cls, context)
            if (ancestorParams != null || cls == pyClass) {
                for (field in cls.classAttributes) {
                    val fieldName = field.name
                    if (fieldName != null && !seenNames.contains(fieldName)) {
                        result.add(field to cls)
                        seenNames.add(fieldName)
                    }
                }
            }
        }
        
        return result
    }
    
    private fun buildDataclassParamsInfo(
        params: com.jetbrains.python.codeInsight.PyDataclassParameters
    ): String {
        val html = HtmlBuilder()
        html.append(styledSpan("Dataclass options:", PyHighlighter.PY_KEYWORD))
        html.br()
        
        val options = listOf(
            "init" to params.init,
            "repr" to params.repr,
            "eq" to params.eq,
            "order" to params.order,
            "frozen" to params.frozen,
            "slots" to params.slots
        ).filter { it.second }
        
        if (options.isNotEmpty()) {
            html.append("  ${options.joinToString(", ") { it.first }}")
        }
        
        return html.toString()
    }
}
```

#### Phase 2: Pydantic Support via PyDataclassParametersProvider

**File**: `PydanticDataclassProvider.kt`

```kotlin
package com.example.pycharm.pydantic

import com.intellij.openapi.project.Project
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.PyDataclassParametersProvider
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyCallableParameter
import com.jetbrains.python.psi.types.TypeEvalContext

class PydanticDataclassProvider : PyDataclassParametersProvider {
    
    companion object {
        private val PYDANTIC_BASE_MODEL = QualifiedName.fromDottedString("pydantic.BaseModel")
        private val PYDANTIC_DATACLASS = QualifiedName.fromDottedString("pydantic.dataclasses.dataclass")
        
        // Custom type for Pydantic
        object PydanticType : PyDataclassParameters.Type {
            override val asPredefinedType: PyDataclassParameters.PredefinedType? = null
            override fun toString() = "PYDANTIC"
        }
    }
    
    override fun getType(): PyDataclassParameters.Type = PydanticType
    
    override fun getDecoratorAndTypeAndParameters(
        project: Project
    ): Triple<QualifiedName, PyDataclassParameters.Type, List<PyCallableParameter>>? {
        // Register pydantic.dataclasses.dataclass decorator
        return Triple(PYDANTIC_DATACLASS, PydanticType, emptyList())
    }
    
    override fun getDataclassParameters(
        cls: PyClass,
        context: TypeEvalContext?
    ): PyDataclassParameters? {
        // Check if class inherits from pydantic.BaseModel
        if (context != null && isPydanticModel(cls, context)) {
            return PyDataclassParameters(
                init = true,
                repr = true,
                eq = true,
                order = false,
                unsafeHash = false,
                frozen = false,
                matchArgs = true,
                kwOnly = false,
                slots = false,
                initArgument = null,
                reprArgument = null,
                eqArgument = null,
                orderArgument = null,
                unsafeHashArgument = null,
                frozenArgument = null,
                matchArgsArgument = null,
                kwOnlyArgument = null,
                slotsArgument = null,
                type = PydanticType,
                others = emptyMap()
            )
        }
        return null
    }
    
    private fun isPydanticModel(cls: PyClass, context: TypeEvalContext): Boolean {
        // Check if any ancestor is pydantic.BaseModel
        return cls.getAncestorClasses(context).any { ancestor ->
            ancestor.qualifiedName == "pydantic.BaseModel" ||
            ancestor.qualifiedName == "pydantic.main.BaseModel"
        }
    }
}
```

#### Phase 3: Enhanced Quick Info for Pydantic Models

**File**: `PydanticQuickInfoProvider.kt`

```kotlin
package com.example.pycharm.pydantic

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.psi.PsiElement
import com.jetbrains.python.documentation.PythonDocumentationQuickInfoProvider
import com.jetbrains.python.documentation.styledSpan
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.TypeEvalContext

class PydanticQuickInfoProvider : PythonDocumentationQuickInfoProvider {
    
    override fun getQuickInfo(originalElement: PsiElement): String? {
        val pyClass = when (originalElement) {
            is PyReferenceExpression -> originalElement.reference.resolve() as? PyClass
            is PyClass -> originalElement
            else -> null
        } ?: return null
        
        val context = TypeEvalContext.userInitiated(
            pyClass.project,
            pyClass.containingFile
        )
        
        // Check if it's a Pydantic model
        if (!isPydanticModel(pyClass, context)) return null
        
        return buildPydanticQuickInfo(pyClass, context)
    }
    
    private fun isPydanticModel(cls: PyClass, context: TypeEvalContext): Boolean {
        return cls.getAncestorClasses(context).any { ancestor ->
            ancestor.qualifiedName == "pydantic.BaseModel" ||
            ancestor.qualifiedName == "pydantic.main.BaseModel"
        }
    }
    
    private fun buildPydanticQuickInfo(
        pyClass: PyClass,
        context: TypeEvalContext
    ): String {
        val html = HtmlBuilder()
        
        // Header
        html.append(styledSpan("Pydantic Model", PyHighlighter.PY_DECORATOR))
        html.br()
        html.append(styledSpan("class ", PyHighlighter.PY_KEYWORD))
        html.append(styledSpan(pyClass.name ?: "Unknown", PyHighlighter.PY_CLASS_DEFINITION))
        html.append(styledSpan("(BaseModel)", PyHighlighter.PY_BUILTIN_NAME))
        html.append(styledSpan(":", PyHighlighter.PY_OPERATION_SIGN))
        html.br()
        
        // Fields section
        html.br()
        html.append(styledSpan("Fields:", PyHighlighter.PY_KEYWORD))
        html.br()
        
        val fields = collectPydanticFields(pyClass, context)
        
        if (fields.isEmpty()) {
            html.append("  (no fields)")
        } else {
            fields.forEach { (field, sourceClass, fieldInfo) ->
                html.append("  ")
                html.append(styledSpan(field.name ?: "unknown", PyHighlighter.PY_PARAMETER))
                
                val fieldType = context.getType(field)
                if (fieldType != null) {
                    html.append(styledSpan(": ", PyHighlighter.PY_OPERATION_SIGN))
                    val typeName = PythonDocumentationProvider.getTypeName(fieldType, context)
                    html.append(styledSpan(typeName, PyHighlighter.PY_ANNOTATION))
                }
                
                // Pydantic-specific field info
                if (fieldInfo.isRequired) {
                    html.append(styledSpan(" [required]", PyHighlighter.PY_KEYWORD))
                } else {
                    html.append(styledSpan(" [optional]", PyHighlighter.PY_COMMENT))
                }
                
                if (sourceClass != pyClass) {
                    html.append(styledSpan(" (from ${sourceClass.name})", PyHighlighter.PY_COMMENT))
                }
                
                html.br()
            }
        }
        
        return html.toString()
    }
    
    private data class PydanticFieldInfo(
        val isRequired: Boolean,
        val hasDefault: Boolean
    )
    
    private fun collectPydanticFields(
        pyClass: PyClass,
        context: TypeEvalContext
    ): List<Triple<com.jetbrains.python.psi.PyTargetExpression, PyClass, PydanticFieldInfo>> {
        val result = mutableListOf<Triple<com.jetbrains.python.psi.PyTargetExpression, PyClass, PydanticFieldInfo>>()
        val seenNames = mutableSetOf<String>()
        
        val classesToCheck = mutableListOf(pyClass)
        classesToCheck.addAll(pyClass.getAncestorClasses(context).filter { 
            isPydanticModel(it, context) 
        })
        
        for (cls in classesToCheck) {
            for (field in cls.classAttributes) {
                val fieldName = field.name
                if (fieldName != null && !seenNames.contains(fieldName)) {
                    val hasDefault = field.findAssignedValue() != null
                    val fieldInfo = PydanticFieldInfo(
                        isRequired = !hasDefault,
                        hasDefault = hasDefault
                    )
                    result.add(Triple(field, cls, fieldInfo))
                    seenNames.add(fieldName)
                }
            }
        }
        
        return result
    }
}
```

#### Phase 4: Plugin Configuration

**File**: `plugin.xml`

```xml
<idea-plugin>
    <id>com.example.pycharm.dataclass-enhanced-info</id>
    <name>Enhanced Dataclass Quick Info</name>
    <vendor>Your Name</vendor>
    <description>
        Provides enhanced quick info for Python dataclasses and Pydantic models,
        displaying field names, types, and inheritance information.
    </description>
    
    <depends>com.intellij.modules.python</depends>
    
    <extensions defaultExtensionNs="Pythonid">
        <!-- Register quick info providers -->
        <pythonDocumentationQuickInfoProvider 
            implementation="com.example.pycharm.dataclass.DataclassQuickInfoProvider"
            order="first"/>
        
        <pythonDocumentationQuickInfoProvider 
            implementation="com.example.pycharm.pydantic.PydanticQuickInfoProvider"
            order="first"/>
        
        <!-- Register Pydantic as dataclass-like -->
        <pyDataclassParametersProvider 
            implementation="com.example.pycharm.pydantic.PydanticDataclassProvider"/>
    </extensions>
</idea-plugin>
```

---

### Test Cases

#### Test Case 1: Basic Dataclass Quick Info

```python
from dataclasses import dataclass

@dataclass
class Person:
    name: str
    age: int
    email: str = "unknown@example.com"
```

**Expected Output** (when hovering over `Person`):
```
@dataclass
class Person:

Fields:
  name: str
  age: int
  email: str = "unknown@example.com"

Dataclass options:
  init, repr, eq
```

#### Test Case 2: Dataclass with Inheritance

```python
from dataclasses import dataclass

@dataclass
class Animal:
    name: str
    species: str

@dataclass
class Dog(Animal):
    breed: str
    age: int
```

**Expected Output** (when hovering over `Dog`):
```
@dataclass
class Dog(Animal):

Fields:
  breed: str
  age: int
  name: str (from Animal)
  species: str (from Animal)

Dataclass options:
  init, repr, eq
```

#### Test Case 3: Pydantic BaseModel

```python
from pydantic import BaseModel

class User(BaseModel):
    id: int
    username: str
    email: str
    is_active: bool = True
```

**Expected Output** (when hovering over `User`):
```
Pydantic Model
class User(BaseModel):

Fields:
  id: int [required]
  username: str [required]
  email: str [required]
  is_active: bool [optional]
```

#### Test Case 4: Pydantic with Inheritance

```python
from pydantic import BaseModel

class BaseUser(BaseModel):
    id: int
    username: str

class AdminUser(BaseUser):
    permissions: list[str]
    is_superuser: bool = False
```

**Expected Output** (when hovering over `AdminUser`):
```
Pydantic Model
class AdminUser(BaseModel):

Fields:
  permissions: list[str] [required]
  is_superuser: bool [optional]
  id: int [required] (from BaseUser)
  username: str [required] (from BaseUser)
```

#### Test Case 5: Frozen Dataclass

```python
from dataclasses import dataclass

@dataclass(frozen=True, slots=True)
class Point:
    x: float
    y: float
```

**Expected Output**:
```
@dataclass
class Point:

Fields:
  x: float
  y: float

Dataclass options:
  init, repr, eq, frozen, slots
```

#### Test Case 6: attrs Class

```python
import attr

@attr.s(auto_attribs=True)
class Config:
    host: str
    port: int = 8080
    debug: bool = False
```

**Expected Output**:
```
@attr.s
class Config:

Fields:
  host: str
  port: int = 8080
  debug: bool = False

Dataclass options:
  init, repr, eq
```

---

### Testing Strategy

#### Unit Tests

**File**: `DataclassQuickInfoProviderTest.kt`

```kotlin
package com.example.pycharm.dataclass

import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyClass

class DataclassQuickInfoProviderTest : PyTestCase() {
    
    fun testBasicDataclass() {
        myFixture.configureByText("test.py", """
            from dataclasses import dataclass
            
            @dataclass
            class Person:
                name: str
                age: int
        """.trimIndent())
        
        val pyClass = myFixture.findElementByText("Person", PyClass::class.java)
        val provider = DataclassQuickInfoProvider()
        val quickInfo = provider.getQuickInfo(pyClass)
        
        assertNotNull(quickInfo)
        assertContainsElements(quickInfo!!, "name: str", "age: int", "@dataclass")
    }
    
    fun testDataclassWithInheritance() {
        myFixture.configureByText("test.py", """
            from dataclasses import dataclass
            
            @dataclass
            class Animal:
                species: str
            
            @dataclass
            class Dog(Animal):
                breed: str
        """.trimIndent())
        
        val pyClass = myFixture.findElementByText("Dog", PyClass::class.java)
        val provider = DataclassQuickInfoProvider()
        val quickInfo = provider.getQuickInfo(pyClass)
        
        assertNotNull(quickInfo)
        assertContainsElements(quickInfo!!, "breed: str", "species: str", "(from Animal)")
    }
    
    fun testNonDataclass() {
        myFixture.configureByText("test.py", """
            class RegularClass:
                def __init__(self):
                    self.x = 1
        """.trimIndent())
        
        val pyClass = myFixture.findElementByText("RegularClass", PyClass::class.java)
        val provider = DataclassQuickInfoProvider()
        val quickInfo = provider.getQuickInfo(pyClass)
        
        assertNull(quickInfo) // Should return null for non-dataclasses
    }
}
```

#### Integration Tests

**File**: `PydanticQuickInfoProviderTest.kt`

```kotlin
package com.example.pycharm.pydantic

import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyClass

class PydanticQuickInfoProviderTest : PyTestCase() {
    
    override fun setUp() {
        super.setUp()
        // Add pydantic to test environment
        myFixture.copyDirectoryToProject("pydantic", "pydantic")
    }
    
    fun testPydanticBaseModel() {
        myFixture.configureByText("test.py", """
            from pydantic import BaseModel
            
            class User(BaseModel):
                id: int
                name: str
                email: str = "default@example.com"
        """.trimIndent())
        
        val pyClass = myFixture.findElementByText("User", PyClass::class.java)
        val provider = PydanticQuickInfoProvider()
        val quickInfo = provider.getQuickInfo(pyClass)
        
        assertNotNull(quickInfo)
        assertContainsElements(quickInfo!!, 
            "Pydantic Model", 
            "id: int [required]",
            "name: str [required]",
            "email: str [optional]"
        )
    }
}
```

---

### Additional Enhancements

#### 1. Configuration Options

Add settings to customize the display:

```kotlin
// Settings configurable
class DataclassQuickInfoSettings {
    var showInheritedFields: Boolean = true
    var showDataclassOptions: Boolean = true
    var maxFieldsToShow: Int = 20
    var showFieldDefaults: Boolean = true
}
```

#### 2. Performance Optimization

```kotlin
// Cache results
private val quickInfoCache = CachedValuesManager.getManager(project)
    .createCachedValue {
        CachedValueProvider.Result.create(
            computeQuickInfo(pyClass, context),
            PsiModificationTracker.MODIFICATION_COUNT
        )
    }
```

#### 3. Additional Hover Info

Implement `getHoverAdditionalQuickInfo` for more detailed information:

```kotlin
override fun getHoverAdditionalQuickInfo(
    context: TypeEvalContext,
    originalElement: PsiElement?
): String? {
    // Add validation rules, field constraints, etc.
    return buildDetailedFieldInfo(originalElement, context)
}
```

---

### Summary

This implementation plan provides:

1. **Extension Points**: Uses `PythonDocumentationQuickInfoProvider` for quick info and `PyDataclassParametersProvider` for Pydantic support
2. **Reusable Components**: Leverages existing PyCharm infrastructure for styling, type resolution, and dataclass detection
3. **Comprehensive Coverage**: Supports standard dataclasses, attrs, dataclass_transform, and Pydantic models
4. **Inheritance Support**: Properly displays fields from parent classes
5. **Test Cases**: Complete test coverage for various scenarios
6. **Extensibility**: Easy to add support for other dataclass-like libraries

The plugin integrates seamlessly with PyCharm's existing documentation system while providing enhanced information specifically for dataclass-like structures.