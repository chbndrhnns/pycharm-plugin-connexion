### Analysis: Indexing Assignment-Pattern Fixtures via the Stub Index

### The Existing Infrastructure

The platform already has a perfect parallel mechanism for this. Just as `PyDecoratorStubIndex` + `PyCustomDecoratorStubType` handles `@decorator` syntax, there's an analogous system for assignment targets:

- **`CustomTargetExpressionStubType<T>`** — an extension point (`Pythonid.customTargetExpressionStubType`) for creating custom stubs on `PyTargetExpression` nodes (i.e., the left-hand side of assignments)
- **`PyTargetExpressionElementType.indexStub()`** — already iterates all registered `CustomTargetExpressionStubType` extensions and calls their `indexStub(stub, sink)` method
- **Existing implementations**: `PropertyStubType` (for `property()` calls), `PyNamedTupleStubType`, `PyTypingAliasStubType`

### What It Would Look Like

You'd create two things:

**1. A custom stub + stub type** (in `python-psi-impl`):

```kotlin
// PyTestFixtureTargetStub.kt
class PyTestFixtureTargetStub(val fixtureName: String) : CustomTargetExpressionStub { ... }

// PyTestFixtureTargetStubType.kt  
class PyTestFixtureTargetStubType : CustomTargetExpressionStubType<PyTestFixtureTargetStub>() {
    override fun createStub(psi: PyTargetExpression): PyTestFixtureTargetStub? {
        // Check if assigned value matches: pytest.fixture()(impl_func)
        val outerCall = psi.findAssignedValue() as? PyCallExpression ?: return null
        val innerCall = outerCall.callee as? PyCallExpression ?: return null
        val callee = innerCall.callee as? PyReferenceExpression ?: return null
        val qName = callee.asQualifiedName()?.toString() ?: return null
        if (qName !in TEST_FIXTURE_DECORATOR_NAMES) return null
        return PyTestFixtureTargetStub(psi.name ?: return null)
    }
    
    override fun indexStub(stub: PyTargetExpressionStub, sink: IndexSink) {
        // Index into a new stub index key for assigned fixtures
        stub.getCustomStub(PyTestFixtureTargetStub::class.java)?.let {
            sink.occurrence(PyAssignedFixtureIndex.KEY, it.fixtureName)
        }
    }
    
    override fun deserializeStub(stream: StubInputStream) = ...
}
```

**2. A new stub index** (or reuse an existing one):

```kotlin
class PyAssignedFixtureIndex : StringStubIndexExtension<PyTargetExpression>() {
    companion object {
        val KEY = StubIndexKey.createIndexKey<String, PyTargetExpression>("Python.AssignedFixture")
    }
    override fun getKey() = KEY
}
```

Then register the stub type via `plugin.xml`:
```xml
<extensions defaultExtensionNs="Pythonid">
    <customTargetExpressionStubType implementation="...PyTestFixtureTargetStubType"/>
</extensions>
```

And in `PyTestFixture.kt`, replace the current `findAssignedFixtures()` (which scans all conftest.py files via `FilenameIndex`) with a stub-index lookup — similar to how `findDecoratorsByName` works.

### Is It Too Broad or Incorrect?

**It's not too broad** — it's actually the *correct* architectural approach. Here's why:

- **`PropertyStubType` is the exact precedent**: it already does this for `x = property(getter, setter)` — recognizing a call-expression pattern on the RHS of an assignment and creating a custom stub. Your case (`x = pytest.fixture()(func)`) is the same concept.

- **It's scoped correctly**: `createStub()` only fires for `PyTargetExpression` nodes that match the pattern. It won't index random assignments — only those where the RHS is `pytest.fixture()(something)`.

- **Performance win**: The current `findAssignedFixtures()` implementation uses `FilenameIndex.getFilesByName(CONFTEST_PY)` and then walks all statements in each file. A stub index lookup would be O(1) per fixture name instead of O(files × statements).

- **Limitation to note**: The stub is created from the AST during indexing, so `callee.asQualifiedName()` may not fully resolve imports (stubs don't have full resolve context). You'd need to match on the syntactic qualified name (e.g., `pytest.fixture`) rather than the fully resolved one. This is the same constraint `PyDecoratorStubIndex` has — it indexes by the decorator's syntactic name. For `pytest.fixture`, this works fine since the import is almost always `import pytest`.

### Summary

This is the right approach and follows existing patterns (`PropertyStubType`, `PyNamedTupleStubType`). It would replace the current file-scanning `findAssignedFixtures()` with a proper stub-indexed lookup, making it both faster and architecturally consistent with how decorator-based fixtures are found.
