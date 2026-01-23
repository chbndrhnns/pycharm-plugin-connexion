### Extending Index and Cache for Lambda Support

Based on the current implementation in `PyProtocolDefinitionsSearchExecutor.kt`, here's how to extend the optimization plan to include lambdas.

---

### Current Lambda Search Bottleneck

The `findLambdasMatchingCallableProtocol()` method (lines 118-161) has O(n×m) complexity:

```kotlin
// Current approach - iterates ALL Python files, then ALL lambdas
val pythonFiles = FileTypeIndex.getFiles(PythonFileType.INSTANCE, scope)
for (virtualFile in pythonFiles) {
    val lambdas = PsiTreeUtil.findChildrenOfType(psiFile, PyLambdaExpression::class.java)
    for (lambda in lambdas) {
        val expectedType = getExpectedTypeForLambda(lambda, context)
        // ... check compatibility
    }
}
```

---

### Optimization Strategy 1: Lambda Context Index

#### Implementation Plan

**Step 1: Create `PyLambdaExpectedTypeIndex`**

Create a stub index that maps protocol/type names to lambdas used in contexts expecting that type.

```kotlin
// File: src/main/kotlin/.../index/PyLambdaExpectedTypeIndex.kt

class PyLambdaExpectedTypeIndex : StringStubIndexExtension<PyLambdaExpression>() {
    companion object {
        val KEY: StubIndexKey<String, PyLambdaExpression> = 
            StubIndexKey.createIndexKey("Py.lambda.expectedType")
        
        fun findLambdasExpectingType(
            typeName: String, 
            project: Project, 
            scope: GlobalSearchScope
        ): Collection<PyLambdaExpression> {
            return StubIndex.getElements(KEY, typeName, project, scope, PyLambdaExpression::class.java)
        }
    }
    
    override fun getKey(): StubIndexKey<String, PyLambdaExpression> = KEY
    override fun getVersion(): Int = 1
}
```

**Step 2: Create Indexer for Lambda Expected Types**

Index lambdas based on their usage context during stub creation:

```kotlin
// File: src/main/kotlin/.../index/PyLambdaExpectedTypeIndexer.kt

class PyLambdaExpectedTypeIndexer {
    
    fun indexLambda(lambda: PyLambdaExpression, sink: IndexSink) {
        val expectedTypeName = inferExpectedTypeName(lambda)
        if (expectedTypeName != null) {
            sink.occurrence(PyLambdaExpectedTypeIndex.KEY, expectedTypeName)
        }
    }
    
    private fun inferExpectedTypeName(lambda: PyLambdaExpression): String? {
        val parent = lambda.parent
        
        // Case 1: Lambda as function argument
        if (parent is PyArgumentList) {
            val call = parent.parent as? PyCallExpression ?: return null
            val callee = call.callee as? PyReferenceExpression ?: return null
            val argIndex = parent.arguments.indexOf(lambda)
            
            // Index by: "functionName:argIndex" or resolved type name
            // This allows quick lookup when searching for protocol implementations
            return "${callee.name}:$argIndex"
        }
        
        // Case 2: Lambda assigned to typed variable
        if (parent is PyAssignmentStatement) {
            val target = parent.targets.firstOrNull() as? PyTargetExpression ?: return null
            val annotation = target.annotation?.value as? PyReferenceExpression
            return annotation?.name
        }
        
        return null
    }
}
```

**Step 3: Update `findLambdasMatchingCallableProtocol()` to use index**

```kotlin
private fun findLambdasMatchingCallableProtocol(
    protocol: PyClass,
    scope: GlobalSearchScope,
    context: TypeEvalContext
): Collection<PyLambdaExpression> {
    val protocolName = protocol.name ?: return emptyList()
    val protocolQName = protocol.qualifiedName
    
    // Direct lookup via index - O(1) instead of O(n*m)
    val candidateLambdas = mutableSetOf<PyLambdaExpression>()
    
    // Search by simple name
    candidateLambdas.addAll(
        PyLambdaExpectedTypeIndex.findLambdasExpectingType(protocolName, protocol.project, scope)
    )
    
    // Search by qualified name if different
    if (protocolQName != null && protocolQName != protocolName) {
        candidateLambdas.addAll(
            PyLambdaExpectedTypeIndex.findLambdasExpectingType(protocolQName, protocol.project, scope)
        )
    }
    
    // Filter candidates by actual compatibility
    return candidateLambdas.filter { lambda ->
        val lambdaType = context.getType(lambda) as? PyCallableType ?: return@filter false
        PyProtocolImplementationsSearch.isCallableCompatibleWithCallProtocol(lambdaType, protocol, context)
    }
}
```

---

### Optimization Strategy 2: Lambda Cache

#### Implementation Plan

**Step 1: Add lambda-specific cache to `PyProtocolImplementationsSearch`**

```kotlin
// Add to PyProtocolImplementationsSearch.kt

object PyProtocolImplementationsSearch {
    
    // Existing class cache
    private val classCache = ConcurrentHashMap<CacheKey, CachedClassResult>()
    
    // NEW: Lambda cache for callable protocols
    private val lambdaCache = ConcurrentHashMap<LambdaCacheKey, CachedLambdaResult>()
    
    data class LambdaCacheKey(
        val protocolQName: String,
        val scopeHash: Int,
        val modificationStamp: Long
    )
    
    data class CachedLambdaResult(
        val lambdas: Collection<PyLambdaExpression>,
        val timestamp: Long
    )
    
    private const val LAMBDA_CACHE_TTL_MS = 15_000L  // Shorter TTL for lambdas (more volatile)
    
    fun searchLambdasCached(
        protocol: PyClass,
        scope: GlobalSearchScope,
        context: TypeEvalContext
    ): Collection<PyLambdaExpression> {
        if (!isCallableOnlyProtocol(protocol, context)) {
            return emptyList()
        }
        
        val qName = protocol.qualifiedName ?: return searchLambdas(protocol, scope, context)
        val modStamp = protocol.containingFile?.modificationStamp ?: 0L
        val key = LambdaCacheKey(qName, scope.hashCode(), modStamp)
        
        val cached = lambdaCache[key]
        val now = System.currentTimeMillis()
        
        if (cached != null && (now - cached.timestamp) < LAMBDA_CACHE_TTL_MS) {
            if (cached.lambdas.all { it.isValid }) {
                return cached.lambdas
            }
        }
        
        val result = searchLambdas(protocol, scope, context)
        lambdaCache[key] = CachedLambdaResult(result, now)
        
        return result
    }
    
    private fun searchLambdas(
        protocol: PyClass,
        scope: GlobalSearchScope,
        context: TypeEvalContext
    ): Collection<PyLambdaExpression> {
        // Use index-based search (from Strategy 1)
        // ... implementation
    }
    
    fun invalidateLambdaCache() {
        lambdaCache.clear()
    }
    
    fun invalidateLambdaCacheFor(protocolQName: String) {
        lambdaCache.keys.removeIf { it.protocolQName == protocolQName }
    }
}
```

**Step 2: Update cache invalidation listener**

```kotlin
// Update ProtocolCacheInvalidationListener.kt

class ProtocolCacheInvalidationListener : PsiTreeChangeAdapter() {
    
    override fun childrenChanged(event: PsiTreeChangeEvent) {
        if (event.file?.fileType?.name == "Python") {
            PyProtocolImplementationsSearch.invalidateCache()
            PyProtocolImplementationsSearch.invalidateLambdaCache()  // NEW
        }
    }
    
    override fun childAdded(event: PsiTreeChangeEvent) {
        // Specifically invalidate lambda cache when lambdas are added
        if (event.child is PyLambdaExpression) {
            PyProtocolImplementationsSearch.invalidateLambdaCache()
        }
    }
}
```

---

### Testing Plan for Lambda Index and Cache

#### Unit Tests for Lambda Index

```kotlin
// File: src/test/kotlin/.../index/PyLambdaExpectedTypeIndexTest.kt

class PyLambdaExpectedTypeIndexTest : TestBase() {
    
    fun testIndexFindsLambdaPassedAsArgument() {
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class MyProto(Protocol):
                def __call__(self, x: int) -> str: ...
            
            def use_proto(p: MyProto) -> str:
                return p(1)
            
            use_proto(lambda x: str(x))
        """.trimIndent())
        
        val lambdas = PyLambdaExpectedTypeIndex.findLambdasExpectingType(
            "MyProto", project, GlobalSearchScope.fileScope(myFixture.file)
        )
        
        assertEquals(1, lambdas.size)
    }
    
    fun testIndexFindsLambdaAssignedToTypedVariable() {
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class MyProto(Protocol):
                def __call__(self, x: int) -> str: ...
            
            my_func: MyProto = lambda x: str(x)
        """.trimIndent())
        
        val lambdas = PyLambdaExpectedTypeIndex.findLambdasExpectingType(
            "MyProto", project, GlobalSearchScope.fileScope(myFixture.file)
        )
        
        assertEquals(1, lambdas.size)
    }
    
    fun testIndexDoesNotFindUntypedLambda() {
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class MyProto(Protocol):
                def __call__(self, x: int) -> str: ...
            
            # No type context - should NOT be indexed
            untyped = lambda x: str(x)
        """.trimIndent())
        
        val lambdas = PyLambdaExpectedTypeIndex.findLambdasExpectingType(
            "MyProto", project, GlobalSearchScope.fileScope(myFixture.file)
        )
        
        assertEquals(0, lambdas.size)
    }
    
    fun testIndexFindsMultipleLambdasForSameProtocol() {
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class MyProto(Protocol):
                def __call__(self, x: int) -> str: ...
            
            def use_proto(p: MyProto) -> str:
                return p(1)
            
            use_proto(lambda x: str(x))
            use_proto(lambda x: f"value: {x}")
            
            typed_var: MyProto = lambda x: str(x * 2)
        """.trimIndent())
        
        val lambdas = PyLambdaExpectedTypeIndex.findLambdasExpectingType(
            "MyProto", project, GlobalSearchScope.fileScope(myFixture.file)
        )
        
        assertEquals(3, lambdas.size)
    }
}
```

#### Unit Tests for Lambda Cache

```kotlin
// File: src/test/kotlin/.../search/PyProtocolLambdaCacheTest.kt

class PyProtocolLambdaCacheTest : TestBase() {
    
    override fun setUp() {
        super.setUp()
        PyProtocolImplementationsSearch.invalidateLambdaCache()
    }
    
    fun testCachedLambdaSearchReturnsSameResults() {
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class My<caret>Proto(Protocol):
                def __call__(self, x: int) -> str: ...
            
            def use_proto(p: MyProto) -> str:
                return p(1)
            
            use_proto(lambda x: str(x))
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val result1 = PyProtocolImplementationsSearch.searchLambdasCached(protocol, scope, context)
        val result2 = PyProtocolImplementationsSearch.searchLambdasCached(protocol, scope, context)
        
        assertEquals(result1.size, result2.size)
    }
    
    fun testCachedLambdaSearchIsFaster() {
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class My<caret>Proto(Protocol):
                def __call__(self, x: int) -> str: ...
            
            def use_proto(p: MyProto) -> str:
                return p(1)
            
            use_proto(lambda x: str(x))
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        // First call - populates cache
        val start1 = System.nanoTime()
        PyProtocolImplementationsSearch.searchLambdasCached(protocol, scope, context)
        val duration1 = System.nanoTime() - start1
        
        // Second call - should use cache
        val start2 = System.nanoTime()
        PyProtocolImplementationsSearch.searchLambdasCached(protocol, scope, context)
        val duration2 = System.nanoTime() - start2
        
        assertTrue("Cached lambda search should be faster", duration2 < duration1)
    }
}
```

#### Performance Tests

```kotlin
// File: src/test/kotlin/.../search/PyProtocolLambdaPerformanceTest.kt

class PyProtocolLambdaPerformanceTest : TestBase() {
    
    fun testLambdaSearchPerformanceWithManyLambdas() {
        // Generate a file with many lambdas
        val lambdaUsages = (1..50).joinToString("\n") { i ->
            "use_proto(lambda x: str(x + $i))"
        }
        
        myFixture.configureByText("test.py", """
            class Protocol: pass
            
            class My<caret>Proto(Protocol):
                def __call__(self, x: int) -> str: ...
            
            def use_proto(p: MyProto) -> str:
                return p(1)
            
            $lambdaUsages
        """.trimIndent())
        
        val protocol = myFixture.elementAtCaret as PyClass
        val context = TypeEvalContext.codeAnalysis(project, myFixture.file)
        val scope = GlobalSearchScope.fileScope(myFixture.file)
        
        val startTime = System.currentTimeMillis()
        val lambdas = PyProtocolImplementationsSearch.searchLambdasCached(protocol, scope, context)
        val duration = System.currentTimeMillis() - startTime
        
        assertEquals(50, lambdas.size)
        assertTrue("Lambda search should complete in under 500ms, took ${duration}ms", duration < 500)
    }
}
```

---

### Implementation Order (Extended)

| Phase | Task | Estimated Effort |
|-------|------|------------------|
| 1 | Implement `PyClassMembersIndex` (from original plan) | 2-3 hours |
| 2 | Write class index unit tests | 1-2 hours |
| 3 | **NEW: Implement `PyLambdaExpectedTypeIndex`** | 2-3 hours |
| 4 | **NEW: Write lambda index unit tests** | 1-2 hours |
| 5 | Update `findCandidateClasses()` to use index | 30 min |
| 6 | **NEW: Update `findLambdasMatchingCallableProtocol()` to use index** | 1 hour |
| 7 | Implement class caching | 1-2 hours |
| 8 | **NEW: Implement lambda caching** | 1-2 hours |
| 9 | Add cache invalidation listener (combined) | 1 hour |
| 10 | Write all cache unit tests | 2 hours |
| 11 | Write performance tests | 1 hour |
| 12 | Integration testing & bug fixes | 2-3 hours |

**Total estimated effort: 15-22 hours** (extended from 10-15 hours)

---

### Expected Performance Improvements (Including Lambdas)

| Scenario | Before | After (Index) | After (Index + Cache) |
|----------|--------|---------------|----------------------|
| Class search (1000 classes) | ~500ms | ~50ms | ~1ms (cached) |
| Lambda search (100 files, 500 lambdas) | ~2000ms | ~100ms | ~1ms (cached) |
| Combined protocol search | ~2500ms | ~150ms | ~2ms (cached) |

---

### Running Tests

```bash
# Run all index tests
./gradlew test --tests "com.github.chbndrhnns.betterpy.core.index.*"

# Run lambda-specific tests
./gradlew test --tests "com.github.chbndrhnns.betterpy.core.index.PyLambdaExpectedTypeIndexTest"

# Run lambda cache tests
./gradlew test --tests "com.github.chbndrhnns.betterpy.features.search.PyProtocolLambdaCacheTest"

# Run all performance tests
./gradlew test --tests "*PerformanceTest"
```

---

### Key Considerations for Lambda Indexing

1. **Stub Index Limitations**: Lambda expressions may not have full stub support in PyCharm. You may need to use a file-based index (`FileBasedIndex`) instead of `StubIndex` for lambdas.

2. **Context Sensitivity**: Unlike classes, lambdas derive their "protocol type" from usage context. The index must capture this context relationship.

3. **Cache Volatility**: Lambdas are often added/removed more frequently than classes. Consider a shorter TTL for the lambda cache (15 seconds vs 30 seconds for classes).

4. **Fallback Strategy**: If indexing proves too complex, consider a hybrid approach: use the index for classes but keep the current file-scanning approach for lambdas (with caching only).