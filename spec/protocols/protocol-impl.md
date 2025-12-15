# Implementation Plan: Find Protocol Implementations in PyCharm

## Overview

This document describes a detailed plan to implement a feature that finds classes implementing a `typing.Protocol` and makes them available in the "Find Usages" and "Go to Implementation" actions in PyCharm.

## Background

### What is `typing.Protocol`?

Python's `typing.Protocol` (PEP 544) enables structural subtyping (duck typing) with static type checking. Unlike nominal subtyping (explicit inheritance), a class implements a Protocol if it has all the required methods/attributes with compatible signatures, without explicitly inheriting from the Protocol.

```python
from typing import Protocol

class Drawable(Protocol):
    def draw(self) -> None: ...

# This class implements Drawable without explicit inheritance
class Circle:
    def draw(self) -> None:
        print("Drawing circle")

# This also implements Drawable
class Square:
    def draw(self) -> None:
        print("Drawing square")
```

### Current State in PyCharm

PyCharm already has robust Protocol support:
- **Type checking**: `PyTypeChecker.matchProtocols()` performs structural subtyping checks
- **Protocol detection**: `PyProtocols.kt` provides `isProtocol()` and `inspectProtocolSubclass()`
- **Inspection**: `PyProtocolInspection` validates Protocol implementations

However, the "Find Usages" and "Go to Implementation" actions only find **explicit** inheritors via `PyClassInheritorsSearch`, missing structural implementations.

## Architecture Analysis

### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `PyDefinitionsSearch` | `python-psi-impl/.../search/PyDefinitionsSearch.java` | Entry point for "Go to Implementation" |
| `PyClassInheritorsSearch` | `python-psi-impl/.../search/PyClassInheritorsSearch.java` | Finds explicit class inheritors |
| `PyProtocols.kt` | `python-psi-impl/.../typing/PyProtocols.kt` | Protocol utilities (`isProtocol`, `inspectProtocolSubclass`) |
| `PyTypeChecker` | `python-psi-impl/.../types/PyTypeChecker.java` | Type matching including `matchProtocols()` |
| `PyClassNameIndex` | `python-psi-impl/.../stubs/PyClassNameIndex.java` | Stub index for class names |
| `PyClassAttributesIndex` | `python-psi-impl/.../stubs/PyClassAttributesIndex.java` | Stub index for class attributes |

### Extension Points

- `com.intellij.definitionsSearch` - Used by `PyDefinitionsSearch` to provide implementations
- `Pythonid.pyClassInheritorsSearch` - Used by `PyClassInheritorsSearch` for class inheritors

## Implementation Plan

### Phase 1: Core Protocol Implementation Search

#### 1.1 Create `PyProtocolImplementationsSearch`

Create a new search utility that finds structural implementations of a Protocol.

**File**: `python/python-psi-impl/src/com/jetbrains/python/psi/search/PyProtocolImplementationsSearch.kt`

```kotlin
package com.jetbrains.python.psi.search

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Query
import com.intellij.util.QueryFactory
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.codeInsight.typing.inspectProtocolSubclass
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Searches for classes that structurally implement a given Protocol.
 * 
 * Unlike [PyClassInheritorsSearch] which finds explicit inheritors,
 * this search finds classes that satisfy Protocol requirements through
 * structural subtyping (duck typing).
 */
object PyProtocolImplementationsSearch {
    
    /**
     * Search parameters for Protocol implementation search.
     */
    data class SearchParameters(
        val protocol: PyClass,
        val scope: GlobalSearchScope,
        val context: TypeEvalContext
    )
    
    /**
     * Finds all classes that structurally implement the given Protocol.
     * 
     * @param protocol The Protocol class to find implementations for
     * @param scope The search scope
     * @param context Type evaluation context
     * @return Collection of classes implementing the Protocol
     */
    fun search(
        protocol: PyClass,
        scope: GlobalSearchScope,
        context: TypeEvalContext
    ): Collection<PyClass> {
        val protocolType = context.getType(protocol) as? PyClassType ?: return emptyList()
        
        // Verify this is actually a Protocol
        if (!isProtocol(protocolType, context)) {
            return emptyList()
        }
        
        val implementations = mutableListOf<PyClass>()
        val project = protocol.project
        
        // Get required member names from the Protocol
        val requiredMembers = getProtocolRequiredMembers(protocol, context)
        if (requiredMembers.isEmpty()) {
            return emptyList()
        }
        
        // Use the first required member to narrow down candidates
        val primaryMember = requiredMembers.first()
        val candidates = findCandidateClasses(project, scope, primaryMember)
        
        // Check each candidate for Protocol compatibility
        for (candidate in candidates) {
            ProgressManager.checkCanceled()
            
            if (candidate == protocol) continue
            if (candidate.isSubclass(protocol, context)) continue // Already an explicit inheritor
            
            val candidateType = context.getType(candidate) as? PyClassType ?: continue
            
            if (matchesProtocol(protocolType, candidateType, context)) {
                implementations.add(candidate)
            }
        }
        
        return implementations
    }
    
    /**
     * Gets the names of required members defined in the Protocol.
     */
    private fun getProtocolRequiredMembers(protocol: PyClass, context: TypeEvalContext): List<String> {
        val members = mutableListOf<String>()
        
        // Get methods
        for (method in protocol.methods) {
            val name = method.name ?: continue
            if (name.startsWith("_") && !name.startsWith("__")) continue
            if (name in IGNORED_PROTOCOL_MEMBERS) continue
            members.add(name)
        }
        
        // Get class attributes
        for (attr in protocol.classAttributes) {
            val name = attr.name ?: continue
            if (name.startsWith("_")) continue
            members.add(name)
        }
        
        return members
    }
    
    /**
     * Finds candidate classes that might implement the Protocol.
     * Uses stub indexes for efficient lookup.
     */
    private fun findCandidateClasses(
        project: Project,
        scope: GlobalSearchScope,
        memberName: String
    ): Collection<PyClass> {
        // Strategy: Find all classes in scope and filter by member presence
        // For better performance, we could create a dedicated index
        val allClasses = mutableSetOf<PyClass>()
        
        StubIndex.getInstance().processAllKeys(
            PyClassNameIndex.KEY,
            project
        ) { className ->
            ProgressManager.checkCanceled()
            val classes = PyClassNameIndex.find(className, project, scope)
            for (cls in classes) {
                // Quick check: does this class have the required member?
                if (cls.findMethodByName(memberName, false, null) != null ||
                    cls.findClassAttribute(memberName, false, null) != null) {
                    allClasses.add(cls)
                }
            }
            true
        }
        
        return allClasses
    }
    
    /**
     * Checks if a candidate class structurally matches the Protocol.
     */
    private fun matchesProtocol(
        protocol: PyClassType,
        candidate: PyClassType,
        context: TypeEvalContext
    ): Boolean {
        val inspection = inspectProtocolSubclass(protocol, candidate, context)
        
        for ((protocolMember, subclassMembers) in inspection) {
            if (subclassMembers.isEmpty()) {
                return false // Missing required member
            }
            
            // Check type compatibility
            val protocolMemberType = protocolMember.type
            val hasCompatibleMember = subclassMembers.any { subclassMember ->
                val subclassMemberType = subclassMember.type
                // Use PyTypeChecker for proper type matching
                com.jetbrains.python.psi.types.PyTypeChecker.match(
                    protocolMemberType,
                    subclassMemberType,
                    context
                )
            }
            
            if (!hasCompatibleMember) {
                return false
            }
        }
        
        return true
    }
    
    private val IGNORED_PROTOCOL_MEMBERS = setOf(
        "__init__",
        "__new__",
        "__slots__",
        "__class_getitem__",
        "__init_subclass__"
    )
}
```

#### 1.2 Extend `PyDefinitionsSearch`

Modify `PyDefinitionsSearch` to include Protocol implementations.

**File**: `python/python-psi-impl/src/com/jetbrains/python/psi/search/PyDefinitionsSearch.java`

```java
// Add to the execute() method, after the PyClass handling:

if (element instanceof PyClass) {
    if (!processInheritors((PyClass)element, consumer)) return false;
    // NEW: Also search for Protocol implementations
    return processProtocolImplementations((PyClass)element, consumer);
}

// Add new method:
private static boolean processProtocolImplementations(
    @NotNull PyClass pyClass,
    @NotNull Processor<? super PsiElement> consumer
) {
    return ReadAction.compute(() -> {
        TypeEvalContext context = TypeEvalContext.codeAnalysis(
            pyClass.getProject(),
            pyClass.getContainingFile()
        );
        
        PyClassType classType = (PyClassType) context.getType(pyClass);
        if (classType == null || !PyProtocolsKt.isProtocol(classType, context)) {
            return true; // Not a Protocol, nothing to do
        }
        
        GlobalSearchScope scope = GlobalSearchScope.allScope(pyClass.getProject());
        Collection<PyClass> implementations = PyProtocolImplementationsSearch.INSTANCE.search(
            pyClass, scope, context
        );
        
        for (PyClass impl : implementations) {
            if (!consumer.process(impl)) {
                return false;
            }
        }
        return true;
    });
}
```

### Phase 2: Integration with Find Usages

#### 2.1 Create `PyProtocolUsagesSearcher`

Add Protocol implementations to "Find Usages" results.

**File**: `python/python-psi-impl/src/com/jetbrains/python/findUsages/PyProtocolUsagesSearcher.kt`

```kotlin
package com.jetbrains.python.findUsages

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.search.PyProtocolImplementationsSearch
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Extends "Find Usages" to include structural Protocol implementations.
 * 
 * When finding usages of a Protocol class, this searcher adds references
 * to classes that structurally implement the Protocol.
 */
class PyProtocolUsagesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    
    override fun processQuery(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val element = queryParameters.elementToSearch
        if (element !is PyClass) return
        
        val context = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
        val classType = context.getType(element) as? PyClassType ?: return
        
        if (!isProtocol(classType, context)) return
        
        val scope = queryParameters.effectiveSearchScope
        val globalScope = com.intellij.psi.search.GlobalSearchScope.allScope(element.project)
        
        val implementations = PyProtocolImplementationsSearch.search(element, globalScope, context)
        
        // For each implementation, we could add a "virtual" reference
        // This requires creating a custom PsiReference implementation
        // that points to the implementing class
    }
}
```

### Phase 3: UI Integration

#### 3.1 Add "Protocol Implementations" to Find Usages Dialog

Create a custom usage type for Protocol implementations.

**File**: `python/src/com/jetbrains/python/findUsages/PyProtocolImplementationUsageType.kt`

```kotlin
package com.jetbrains.python.findUsages

import com.intellij.psi.PsiElement
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.jetbrains.python.PyBundle
import com.jetbrains.python.psi.PyClass

/**
 * Provides a custom usage type for Protocol implementations.
 */
class PyProtocolImplementationUsageTypeProvider : UsageTypeProvider {
    
    companion object {
        val PROTOCOL_IMPLEMENTATION = UsageType { "Protocol implementation" }
    }
    
    override fun getUsageType(element: PsiElement): UsageType? {
        // This would be used when we have a custom reference type
        // for Protocol implementations
        return null
    }
}
```

#### 3.2 Add Gutter Icon for Protocol Implementations

Add a gutter icon showing Protocol implementations (similar to "Implemented by" for interfaces).

**File**: `python/src/com/jetbrains/python/codeInsight/PyProtocolImplementationsLineMarkerProvider.kt`

```kotlin
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.impl.LineMarkerNavigator
import com.intellij.codeInsight.daemon.impl.MarkerType
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.icons.AllIcons
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.search.PyProtocolImplementationsSearch
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext
import java.awt.event.MouseEvent

/**
 * Provides gutter icons for Protocol classes showing their implementations.
 */
class PyProtocolImplementationsLineMarkerProvider : LineMarkerProvider {
    
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PyClass) return null
        
        val nameIdentifier = element.nameIdentifier ?: return null
        val context = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
        val classType = context.getType(element) as? PyClassType ?: return null
        
        if (!isProtocol(classType, context)) return null
        
        return LineMarkerInfo(
            nameIdentifier,
            nameIdentifier.textRange,
            AllIcons.Gutter.ImplementedMethod,
            { "Protocol implementations" },
            ProtocolImplementationsNavigator(),
            GutterIconRenderer.Alignment.RIGHT,
            { "Protocol implementations" }
        )
    }
    
    private class ProtocolImplementationsNavigator : LineMarkerNavigator() {
        override fun browse(e: MouseEvent, element: PsiElement) {
            val pyClass = element.parent as? PyClass ?: return
            val context = TypeEvalContext.codeAnalysis(pyClass.project, pyClass.containingFile)
            val scope = GlobalSearchScope.allScope(pyClass.project)
            
            val implementations = PyProtocolImplementationsSearch.search(pyClass, scope, context)
            
            if (implementations.isEmpty()) return
            
            PsiElementListNavigator.openTargets(
                e,
                implementations.toTypedArray(),
                "Protocol Implementations",
                "Protocol implementations of ${pyClass.name}",
                DefaultPsiElementCellRenderer()
            )
        }
    }
}
```

### Phase 4: Performance Optimization

#### 4.1 Create Protocol Members Index (Optional)

For large projects, create a dedicated stub index for Protocol member lookups.

**File**: `python/python-psi-impl/src/com/jetbrains/python/psi/stubs/PyProtocolMembersIndex.kt`

```kotlin
package com.jetbrains.python.psi.stubs

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import com.jetbrains.python.psi.PyClass

/**
 * Index mapping method/attribute names to classes that define them.
 * Used for efficient Protocol implementation lookup.
 */
class PyProtocolMembersIndex : StringStubIndexExtension<PyClass>() {
    
    companion object {
        val KEY: StubIndexKey<String, PyClass> = StubIndexKey.createIndexKey("Py.class.members")
    }
    
    override fun getKey(): StubIndexKey<String, PyClass> = KEY
}
```

#### 4.2 Caching Strategy

Implement caching for Protocol implementation results.

```kotlin
// In PyProtocolImplementationsSearch, add caching:

private val cache = ConcurrentHashMap<CacheKey, Collection<PyClass>>()

data class CacheKey(
    val protocolQName: String,
    val scopeHash: Int
)

fun searchCached(
    protocol: PyClass,
    scope: GlobalSearchScope,
    context: TypeEvalContext
): Collection<PyClass> {
    val qName = protocol.qualifiedName ?: return search(protocol, scope, context)
    val key = CacheKey(qName, scope.hashCode())
    
    return cache.computeIfAbsent(key) {
        search(protocol, scope, context)
    }
}
```

### Phase 5: Testing

#### 5.1 Unit Tests for Protocol Implementation Search

**File**: `python/testSrc/com/jetbrains/python/PyProtocolImplementationsTest.kt`

```kotlin
package com.jetbrains.python

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.search.PyProtocolImplementationsSearch
import com.jetbrains.python.psi.types.TypeEvalContext

class PyProtocolImplementationsTest : PyTestCase() {
    
    fun testSimpleProtocolImplementation() {
        myFixture.configureByText("test.py", """
            from typing import Protocol
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None:
                    pass
            
            class Square:
                def draw(self) -> None:
                    pass
            
            class NotDrawable:
                def paint(self) -> None:
                    pass
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)
        
        assertEquals(2, implementations.size)
        assertTrue(implementations.any { it.name == "Circle" })
        assertTrue(implementations.any { it.name == "Square" })
        assertFalse(implementations.any { it.name == "NotDrawable" })
    }
    
    fun testProtocolWithMultipleMethods() {
        myFixture.configureByText("test.py", """
            from typing import Protocol
            
            class Read<caret>Write(Protocol):
                def read(self) -> str: ...
                def write(self, data: str) -> None: ...
            
            class FileHandler:
                def read(self) -> str:
                    return ""
                def write(self, data: str) -> None:
                    pass
            
            class ReadOnly:
                def read(self) -> str:
                    return ""
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)
        
        assertEquals(1, implementations.size)
        assertEquals("FileHandler", implementations.first().name)
    }
    
    fun testProtocolWithAttributes() {
        myFixture.configureByText("test.py", """
            from typing import Protocol
            
            class Has<caret>Name(Protocol):
                name: str
            
            class Person:
                def __init__(self):
                    self.name = "John"
            
            class Anonymous:
                pass
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)
        
        assertEquals(1, implementations.size)
        assertEquals("Person", implementations.first().name)
    }
    
    fun testGoToImplementationForProtocol() {
        myFixture.configureByText("test.py", """
            from typing import Protocol
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None:
                    pass
        """.trimIndent())
        
        val gotoData = CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)
        
        assertTrue(gotoData.targets.isNotEmpty())
        assertTrue(gotoData.targets.any { 
            it is PyClass && it.name == "Circle" 
        })
    }
    
    fun testExplicitInheritorsNotDuplicated() {
        myFixture.configureByText("test.py", """
            from typing import Protocol
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class ExplicitDrawable(Drawable):
                def draw(self) -> None:
                    pass
            
            class ImplicitDrawable:
                def draw(self) -> None:
                    pass
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)
        
        // Should only include ImplicitDrawable, not ExplicitDrawable
        // (explicit inheritors are found by PyClassInheritorsSearch)
        assertEquals(1, implementations.size)
        assertEquals("ImplicitDrawable", implementations.first().name)
    }
    
    fun testGenericProtocol() {
        myFixture.configureByText("test.py", """
            from typing import Protocol, TypeVar
            
            T = TypeVar('T')
            
            class Container<caret>(Protocol[T]):
                def get(self) -> T: ...
                def set(self, value: T) -> None: ...
            
            class Box:
                def __init__(self):
                    self._value = None
                
                def get(self):
                    return self._value
                
                def set(self, value) -> None:
                    self._value = value
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)
        
        assertEquals(1, implementations.size)
        assertEquals("Box", implementations.first().name)
    }
}
```

#### 5.2 Test Data Files

**File**: `python/testData/navigation/protocolImplementations/simple/test.py`

```python
from typing import Protocol

class Drawable(Protocol):
    def draw(self) -> None: ...

class Circle:
    def draw(self) -> None:
        print("Drawing circle")

class Square:
    def draw(self) -> None:
        print("Drawing square")
```

### Phase 6: Registration

#### 6.1 Register Extension Points

**File**: `python/python-psi-impl/resources/intellij.python.psi.impl.xml`

Add the following registrations:

```xml
<!-- Protocol implementations search -->
<definitionsSearch implementation="com.jetbrains.python.psi.search.PyProtocolDefinitionsSearchExecutor"/>

<!-- Protocol usages searcher (optional) -->
<referencesSearch implementation="com.jetbrains.python.findUsages.PyProtocolUsagesSearcher"/>
```

**File**: `python/pluginResources/intellij.python.community.impl.xml`

```xml
<!-- Protocol implementations gutter icon -->
<codeInsight.lineMarkerProvider 
    language="Python" 
    implementationClass="com.jetbrains.python.codeInsight.PyProtocolImplementationsLineMarkerProvider"/>
```

## Implementation Order

1. **Week 1**: Core search functionality
   - Implement `PyProtocolImplementationsSearch`
   - Extend `PyDefinitionsSearch` to include Protocol implementations
   - Basic unit tests

2. **Week 2**: UI Integration
   - Add gutter icons for Protocol implementations
   - Integrate with "Go to Implementation" action
   - More comprehensive tests

3. **Week 3**: Performance & Polish
   - Performance optimization (caching, indexing)
   - Edge case handling
   - Documentation

4. **Week 4**: Testing & Review
   - Integration tests
   - Code review
   - Bug fixes

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Performance with large codebases | High | Use stub indexes, implement caching, limit search scope |
| False positives in structural matching | Medium | Use existing `PyTypeChecker.matchProtocols()` for accurate matching |
| Complex generic Protocols | Medium | Leverage existing generic type handling in PyCharm |
| Breaking existing functionality | High | Comprehensive test coverage, incremental rollout |

## Success Criteria

1. "Go to Implementation" (Ctrl+Alt+B) on a Protocol class shows all structural implementations
2. Gutter icon appears on Protocol classes with implementations
3. Performance is acceptable (< 1 second for typical projects)
4. No false positives or false negatives in common cases
5. All existing tests continue to pass

## References

- [PEP 544 – Protocols: Structural subtyping](https://peps.python.org/pep-0544/)
- [PyCharm Protocol Support](https://www.jetbrains.com/help/pycharm/type-hinting-in-product.html)
- [IntelliJ Platform SDK - Find Usages](https://plugins.jetbrains.com/docs/intellij/find-usages.html)
- Existing code:
  - `PyProtocols.kt` - Protocol utilities
  - `PyTypeChecker.java` - Type matching
  - `PyDefinitionsSearch.java` - Implementation search
  - `PyClassInheritorsSearch.java` - Class inheritors search
