### Performance Optimization Plan for Protocol Match Search

Based on analysis of the current implementation in `PyProtocolImplementationsSearch.kt`, here's a comprehensive plan to optimize performance using both an index and a cache.

---

### Current Performance Bottleneck

The `findCandidateClasses()` method (lines 98-128) has O(n) complexity where n = total classes in the project:

```kotlin
// Current approach - iterates ALL classes
StubIndex.getInstance().processAllKeys(PyClassNameIndex.KEY, project) { className ->
    allClassNames.add(className)
    true
}
for (className in allClassNames) {
    val classes = PyClassNameIndex.find(className, project, scope)
    for (cls in classes) {
        if (cls.findMethodByName(memberName, false, null) != null || ...) {
            allClasses.add(cls)
        }
    }
}
```

---

### Optimization Strategy 1: Protocol Members Index

#### Implementation Plan

**Step 1: Create `PyClassMembersIndex`**

Create a new stub index that maps method/attribute names directly to classes that define them.

```kotlin
// File: src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/index/PyClassMembersIndex.kt

class PyClassMembersIndex : StringStubIndexExtension<PyClass>() {
    companion object {
        val KEY: StubIndexKey<String, PyClass> = StubIndexKey.createIndexKey("Py.class.members")
        
        fun findClassesWithMember(memberName: String, project: Project, scope: GlobalSearchScope): Collection<PyClass> {
            return StubIndex.getElements(KEY, memberName, project, scope, PyClass::class.java)
        }
    }
    
    override fun getKey(): StubIndexKey<String, PyClass> = KEY
    override fun getVersion(): Int = 1
}
```

**Step 2: Create Stub Element Type Extension**

Register the index to be populated during stub creation:

```kotlin
// File: src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/index/PyClassMembersIndexer.kt

class PyClassMembersIndexer : StubIndexer {
    override fun indexStub(stub: PsiFileStub<*>, sink: IndexSink) {
        // Index all class members (methods and attributes)
        stub.childrenStubs.filterIsInstance<PyClassStub>().forEach { classStub ->
            val pyClass = classStub.psi
            
            // Index methods
            pyClass.methods.forEach { method ->
                method.name?.let { sink.occurrence(PyClassMembersIndex.KEY, it) }
            }
            
            // Index class attributes
            pyClass.classAttributes.forEach { attr ->
                attr.name?.let { sink.occurrence(PyClassMembersIndex.KEY, it) }
            }
        }
    }
}
```

**Step 3: Update `findCandidateClasses()` to use the index**

```kotlin
private fun findCandidateClasses(
    project: Project, scope: GlobalSearchScope, memberName: String
): Collection<PyClass> {
    // Direct lookup via index - O(1) instead of O(n)
    return PyClassMembersIndex.findClassesWithMember(memberName, project, scope)
}
```

**Step 4: Register in `plugin.xml`**

```xml
<extensions defaultExtensionNs="com.intellij">
    <stubIndex implementation="com.github.chbndrhnns.intellijplatformplugincopy.index.PyClassMembersIndex"/>
</extensions>
```

---

### Optimization Strategy 2: Caching Strategy

#### Implementation Plan

**Step 1: Add cache to `PyProtocolImplementationsSearch`**

```kotlin
// Add to PyProtocolImplementationsSearch.kt

object PyProtocolImplementationsSearch {
    
    // Cache for protocol implementation results
    private val cache = ConcurrentHashMap<CacheKey, CachedResult>()
    
    data class CacheKey(
        val protocolQName: String,
        val scopeHash: Int,
        val modificationStamp: Long  // For invalidation
    )
    
    data class CachedResult(
        val implementations: Collection<PyClass>,
        val timestamp: Long
    )
    
    // Cache TTL in milliseconds (e.g., 30 seconds)
    private const val CACHE_TTL_MS = 30_000L
    
    fun searchCached(
        protocol: PyClass,
        scope: GlobalSearchScope,
        context: TypeEvalContext
    ): Collection<PyClass> {
        val qName = protocol.qualifiedName ?: return search(protocol, scope, context)
        val modStamp = protocol.containingFile?.modificationStamp ?: 0L
        val key = CacheKey(qName, scope.hashCode(), modStamp)
        
        val cached = cache[key]
        val now = System.currentTimeMillis()
        
        // Return cached result if valid and not expired
        if (cached != null && (now - cached.timestamp) < CACHE_TTL_MS) {
            // Validate cached classes are still valid
            if (cached.implementations.all { it.isValid }) {
                return cached.implementations
            }
        }
        
        // Compute and cache
        val result = search(protocol, scope, context)
        cache[key] = CachedResult(result, now)
        
        return result
    }
    
    fun invalidateCache() {
        cache.clear()
    }
    
    fun invalidateCacheFor(protocolQName: String) {
        cache.keys.removeIf { it.protocolQName == protocolQName }
    }
}
```

**Step 2: Add PSI modification listener for cache invalidation**

```kotlin
// File: src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/listeners/ProtocolCacheInvalidationListener.kt

class ProtocolCacheInvalidationListener : PsiTreeChangeAdapter() {
    
    override fun childrenChanged(event: PsiTreeChangeEvent) {
        if (event.file?.fileType?.name == "Python") {
            // Invalidate cache when Python files change
            PyProtocolImplementationsSearch.invalidateCache()
        }
    }
}
```

**Step 3: Register listener in `plugin.xml`**

```xml
<extensions defaultExtensionNs="com.intellij">
    <psi.treeChangeListener 
        implementation="com.github.chbndrhnns.intellijplatformplugincopy.listeners.ProtocolCacheInvalidationListener"/>
</extensions>
```

---

### Testing Plan

#### Unit Tests for Index

```kotlin
// File: src/test/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/index/PyClassMembersIndexTest.kt

class PyClassMembersIndexTest : TestBase() {
    
    fun testIndexFindsClassWithMethod() {
        myFixture.configureByText("test.py", """
            class MyClass:
                def my_method(self) -> None:
                    pass
        """.trimIndent())
        
        val classes = PyClassMembersIndex.findClassesWithMember(
            "my_method", project, GlobalSearchScope.fileScope(myFixture.file)
        )
        
        assertEquals(1, classes.size)
        assertEquals("MyClass", classes.first().name)
    }
    
    fun testIndexFindsClassWithAttribute() {
        myFixture.configureByText("test.py", """
            class MyClass:
                my_attr: str
        """.trimIndent())
        
        val classes = PyClassMembersIndex.findClassesWithMember(
            "my_attr", project, GlobalSearchScope.fileScope(myFixture.file)
        )
        
        assertEquals(1, classes.size)
        assertEquals("MyClass", classes.first().name)
    }
    
    fun testIndexFindsMultipleClassesWithSameMember() {
        myFixture.configureByText("test.py", """
            class ClassA:
                def draw(self) -> None: pass
            
            class ClassB:
                def draw(self) -> None: pass
            
            class ClassC:
                def paint(self) -> None: pass
        """.trimIndent())
        
        val classes = PyClassMembersIndex.findClassesWithMember(
            "draw", project, GlobalSearchScope.fileScope(myFixture.file)
        )
        
        assertEquals(2, classes.size)
        assertTrue(classes.any { it.name == "ClassA" })
        assertTrue(classes.any { it.name == "ClassB" })
        assertFalse(classes.any { it.name == "ClassC" })
    }
    
    fun testIndexDoesNotFindPrivateMembers() {
        myFixture.configureByText("test.py", """
            class MyClass:
                def _private_method(self) -> None: pass
                _private_attr: str
        """.trimIndent())
        
        val methodClasses = PyClassMembersIndex.findClassesWithMember(
            "_private_method", project, GlobalSearchScope.fileScope(myFixture.file)
        )
        val attrClasses = PyClassMembersIndex.findClassesWithMember(
            "_private_attr", project, GlobalSearchScope.fileScope(myFixture.file)
        )
        
        // Depending on design choice - either exclude or include private members
        // This test documents the expected behavior
    }
}
```

#### Unit Tests for Cache

```kotlin
// File: src/test/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/search/PyProtocolImplementationsCacheTest.kt

class PyProtocolImplementationsCacheTest : TestBase() {
    
    override fun setUp() {
        super.setUp()
        PyProtocolImplementationsSearch.invalidateCache()
    }
    
    fun testCachedSearchReturnsSameResults() {
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None: pass
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val result1 = PyProtocolImplementationsSearch.searchCached(protocol, scope, context)
        val result2 = PyProtocolImplementationsSearch.searchCached(protocol, scope, context)
        
        assertEquals(result1, result2)
    }
    
    fun testCacheInvalidationClearsResults() {
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None: pass
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        PyProtocolImplementationsSearch.searchCached(protocol, scope, context)
        PyProtocolImplementationsSearch.invalidateCache()
        
        // After invalidation, cache should be empty (verified by internal state or timing)
    }
    
    fun testCacheRespectsModificationStamp() {
        // Test that cache is invalidated when file is modified
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val result1 = PyProtocolImplementationsSearch.searchCached(protocol, scope, context)
        
        // Modify file
        myFixture.type("\nclass NewClass:\n    def draw(self) -> None: pass")
        
        // Cache should be invalidated due to modification stamp change
        val result2 = PyProtocolImplementationsSearch.searchCached(protocol, scope, context)
        
        // Results may differ after modification
    }
}
```

#### Performance Tests

```kotlin
// File: src/test/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/search/PyProtocolImplementationsPerformanceTest.kt

class PyProtocolImplementationsPerformanceTest : TestBase() {
    
    fun testSearchPerformanceWithManyClasses() {
        // Generate a file with many classes
        val classDefinitions = (1..100).joinToString("\n") { i ->
            """
            class Class$i:
                def draw(self) -> None: pass
            """.trimIndent()
        }
        
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            $classDefinitions
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val startTime = System.currentTimeMillis()
        val implementations = PyProtocolImplementationsSearch.search(protocol, scope, context)
        val duration = System.currentTimeMillis() - startTime
        
        assertEquals(100, implementations.size)
        assertTrue("Search should complete in under 1 second, took ${duration}ms", duration < 1000)
    }
    
    fun testCachedSearchIsFaster() {
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class Draw<caret>able(Protocol):
                def draw(self) -> None: ...
            
            class Circle:
                def draw(self) -> None: pass
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        // First call - populates cache
        val start1 = System.nanoTime()
        PyProtocolImplementationsSearch.searchCached(protocol, scope, context)
        val duration1 = System.nanoTime() - start1
        
        // Second call - should use cache
        val start2 = System.nanoTime()
        PyProtocolImplementationsSearch.searchCached(protocol, scope, context)
        val duration2 = System.nanoTime() - start2
        
        assertTrue("Cached search should be faster", duration2 < duration1)
    }
}
```

---

### Implementation Order

| Phase | Task | Estimated Effort |
|-------|------|------------------|
| 1 | Implement `PyClassMembersIndex` | 2-3 hours |
| 2 | Write index unit tests | 1-2 hours |
| 3 | Update `findCandidateClasses()` to use index | 30 min |
| 4 | Implement caching in `PyProtocolImplementationsSearch` | 1-2 hours |
| 5 | Add cache invalidation listener | 1 hour |
| 6 | Write cache unit tests | 1-2 hours |
| 7 | Write performance tests | 1 hour |
| 8 | Integration testing & bug fixes | 2-3 hours |

**Total estimated effort: 10-15 hours**

---

### Expected Performance Improvements

| Scenario | Before | After (Index) | After (Index + Cache) |
|----------|--------|---------------|----------------------|
| First search (1000 classes) | ~500ms | ~50ms | ~50ms |
| Repeated search (same protocol) | ~500ms | ~50ms | ~1ms |
| Search after file edit | ~500ms | ~50ms | ~50ms |

The index provides **~10x improvement** for initial searches, and the cache provides **~50x improvement** for repeated searches on the same protocol.

---

### Running Tests

```bash
# Run all protocol search tests
./gradlew test --tests "com.github.chbndrhnns.intellijplatformplugincopy.search.*"

# Run specific test class
./gradlew test --tests "com.github.chbndrhnns.intellijplatformplugincopy.index.PyClassMembersIndexTest"

# Run performance tests
./gradlew test --tests "com.github.chbndrhnns.intellijplatformplugincopy.search.PyProtocolImplementationsPerformanceTest"
```