### How heavy fixtures are created in PyCharm / IntelliJ tests

In PyCharm’s source (and generally in the IntelliJ platform), **“heavy” fixtures** are built around
`HeavyPlatformTestCase` and related infrastructure. They create a *real* project with modules on disk, instead of the
in‑memory light project.

Below is how that works and where to look in the source.

---

### 1. Entry point: `HeavyPlatformTestCase.setUp()`

In `HeavyPlatformTestCase` (you already opened this file), the heavy fixture is bootstrapped in `setUp()`:

```java

@Override
protected void setUp() throws Exception {
    super.setUp();

    initApplication();

    projectTracker = ((TestProjectManager) ProjectManager.getInstance()).startTracking();

    if (myOldSdks == null) {
        myOldSdks = new SdkLeakTracker();
    }

    setUpProject();
    myEditorListenerTracker = new EditorListenerTracker();
    myThreadTracker = new ThreadTracker();
  ...
}
```

Key points:

- `initApplication()` ensures the test application environment is created (no light project here; this is a full app/IDE
  instance).
- `setUpProject()` creates and opens a **real project** and sets up at least one module and an SDK.

---

### 2. Creating the heavy project: `setUpProject()` → `doCreateAndOpenProject()`

```java
protected void setUpProject() throws Exception {
    myProject = doCreateAndOpenProject();

    WriteAction.runAndWait(() ->
            ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
                setUpModule();
                setUpJdk();
            })
    );

    LightPlatformTestCase.clearUncommittedDocuments(getProject());
    ((FileTypeManagerImpl) FileTypeManager.getInstance()).drainReDetectQueue();
    IndexingTestUtil.waitUntilIndexesAreReady(myProject, getIndexingTimeout());
}
```

The important parts:

- `myProject = doCreateAndOpenProject();` actually **creates a project on disk and opens it** through the regular
  project‑opening pipeline.
- Then it sets up the main module and SDK in a write action.

`doCreateAndOpenProject()` looks like this:

```java
protected @NotNull Project doCreateAndOpenProject() {
    OpenProjectTaskBuilder optionBuilder = getOpenProjectOptions();
    Path projectFile = getProjectDirOrFile(isCreateDirectoryBasedProject());
    return Objects.requireNonNull(
            ProjectManagerEx.getInstanceEx().openProject(projectFile, optionBuilder.build()));
}
```

So heavy tests:

- Compute a `Path` for the project (using a temporary directory via `temporaryDirectory.newPath(...)`).
- Call `ProjectManagerEx.openProject(...)` with `OpenProjectTaskBuilder` options.

This is the *same mechanism* used when the IDE opens a normal project, just with test‑specific options.

---

### 3. Where the project file/dir comes from

The project location is created under a temporary directory managed by the test framework:

```java
protected final @NotNull Path getProjectDirOrFile() {
    return getProjectDirOrFile(isCreateDirectoryBasedProject());
}

protected @NotNull Path getProjectDirOrFile(boolean isDirectoryBasedProject) {
    return temporaryDirectory.newPath(
            getProjectFilename() + (isDirectoryBasedProject ? "" : ProjectFileType.DOT_DEFAULT_EXTENSION));
}
```

So:

- `temporaryDirectory` is a `TemporaryDirectory` helper owned by `HeavyPlatformTestCase`.
- Depending on `isCreateDirectoryBasedProject()`, a `.ipr` project file or a directory‑based project is used.

In practice most modern tests either stick to the default or override `isCreateDirectoryBasedProject()` and/or
`getProjectDirOrFile()` when they need special layouts.

---

### 4. Creating modules: `setUpModule()` and friends

After the project is opened, the default module is created:

```java
protected void setUpModule() {
    try {
        WriteCommandAction.writeCommandAction(getProject()).run(() -> myModule = createMainModule());
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
}

protected Module createMainModule() throws IOException {
    return createModule("module1");
}

protected Module createModule(String moduleName) {
    return doCreateRealModule(moduleName);
}
```

`doCreateRealModule` delegates to `doCreateRealModuleIn`, which uses the standard `ModuleManager` API and a physical
module file:

```java
protected Module doCreateRealModule(String moduleName) {
    return doCreateRealModuleIn(moduleName, myProject, getModuleType());
}

protected Module doCreateRealModuleIn(String moduleName,
                                      Project project,
                                      ModuleType<?> moduleType) {
    return doCreateRealModuleIn(moduleName, project, moduleType, Duration.ofMinutes(10));
}

protected Module doCreateRealModuleIn(String moduleName,
                                      Project project,
                                      ModuleType<?> moduleType,
                                      Duration timeout) {
    Path path = temporaryDirectory.newPath(moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    return createModuleAt(moduleName, project, moduleType, path, timeout);
}

protected Module createModuleAt(String moduleName,
                                Project project,
                                ModuleType<?> moduleType,
                                Path path,
                                Duration timeout) {
    return Objects.requireNonNull(
            WriteAction.compute(() ->
                    ModuleManager.getInstance(project)
                            .newModule(path, moduleType.getId())));
}
```

So a **heavy module** is just a normal IntelliJ module created via `ModuleManager.newModule(...)` with a module file
under the temporary directory.

To create *multiple* modules in your own tests (for cross‑module resolution):

- Extend `HeavyPlatformTestCase` (or a PyCharm‑specific heavy base class).
- In `setUp()` after `super.setUp()`, or in your test body, call `createModule("moduleA")`, `createModule("moduleB")`,
  and use `ModuleRootModificationUtil.addDependency(moduleA, moduleB)` to wire dependencies.

---

### 5. Adding content roots and dependencies

`HeavyPlatformTestCase` itself provides helpers, or you can use common test utilities:

- To connect a directory on disk with a module as a source/content root, tests typically use:
    - `PsiTestUtil.addSourceRoot(module, virtualFile)` or
    - `PsiTestUtil.addContentRoot(module, virtualFile)`

- To wire dependencies:

```java
ModuleRootModificationUtil.addDependency(moduleA, moduleB);
```

You’ll see many examples in the platform and PyCharm tests where they:

1. Create several modules with `createModuleAt` / `createModule`.
2. Create folders under the temp directory via `createTestProjectStructure(...)` or manual
   `VfsUtil.createDirectories(...)`.
3. Attach those folders as source roots with `PsiTestUtil` helpers.
4. Add module dependencies.

This is the standard pattern for cross‑module tests.

---

### 6. PyCharm‑specific heavy fixtures

PyCharm itself usually doesn’t reinvent the heavy fixture; it **builds on top of the platform’s heavy test framework**.
Typical patterns you’ll find in the PyCharm sources:

- PyCharm’s base test classes either:
    - Extend `HeavyPlatformTestCase` directly, or
    - Use `IdeaTestFixtureFactory` + `IdeaProjectTestFixture` / `JavaCodeInsightTestFixture` to create a heavy fixture
      with real modules.

Look for classes like:

- `PyTestCase`
- `PyHeavyTestCase`
- or other `*TestCase` in `python/testSrc` that do not extend `LightPlatformCodeInsightFixtureTestCase`.

Inside those, you’ll see:

- `setUp()` calling something like
  `myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(...)` with a **heavy**
  `IdeaProjectTestFixture`.
- Or calls to `initProject(...)` that delegate to the same `openProject` + `ModuleManager` logic described above.

But in all cases, it is the same machinery: **real project via `ProjectManagerEx.openProject`, real modules
via `ModuleManager.newModule`, and roots/dependencies via `ModuleRootModificationUtil` / `PsiTestUtil`.**

---

### 7. How to replicate this in your PyCharm plugin tests

If you want cross‑module PSI resolution in your plugin tests, follow the same pattern:

1. **Use a heavy base**  
   In platform tests: extend `HeavyPlatformTestCase`.  
   In PyCharm plugin tests: either extend a PyCharm heavy test base (if available) or create a fixture using
   `IdeaTestFixtureFactory` that gives you a heavy project.

2. **In `setUp()`**
    - Call `super.setUp()` (or `myFixture.setUp()` if using fixtures).
    - Create extra modules with `createModule("moduleA")`, `createModule("moduleB")` or via the fixture’s module
      builders.
    - Attach source roots to these modules (`PsiTestUtil.addSourceRoot(...)`).
    - Add module dependencies (`ModuleRootModificationUtil.addDependency(moduleA, moduleB)`).

3. **Create PSI files in the right modules**
    - Write test files under the module roots and obtain PSI via `PsiManager` or via the fixture APIs.
    - Run `PsiReference.resolve()` in your test and assert that the target lies in the other module.

This setup mirrors how PyCharm itself creates heavy fixtures internally, and it’s exactly what you need for correct
cross‑module resolution tests.

If you want, paste one of PyCharm’s existing heavy test classes and I can walk through it line‑by‑line to show where the
heavy fixture is wired up for that concrete example.