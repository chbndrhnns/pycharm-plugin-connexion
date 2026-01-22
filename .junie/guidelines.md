### `.junie/guidelines.md` (content to record)

#### Build / configuration notes (project-specific)

- This is an IntelliJ Platform plugin built with `org.jetbrains.intellij.platform` (Gradle IntelliJ Platform plugin). See `build.gradle.kts`.
- Toolchain / versions:
  - Kotlin JVM toolchain: `21` (`kotlin { jvmToolchain(21) }`).
  - Gradle wrapper version: `9.0.0` (from `gradle.properties`).
  - IntelliJ Platform target: `platformType=PY` and `platformVersion=2025.3` (from `gradle.properties`). This means tests and `runIde` are based on a PyCharm / Python platform distribution.
  - Bundled platform plugin dependency: `PythonCore` (`platformBundledPlugins=PythonCore`).
- Key Gradle tasks used in this repo:
  - Run IDE with plugin: `./gradlew runIde`
  - Run UI-test IDE (Robot): `./gradlew runIdeForUiTests` (configured under `intellijPlatformTesting { runIde { ... } }`), with JVM args including `-Drobot-server.port=8082`.
  - Plugin verification uses `intellijPlatform { pluginVerification { ides { recommended() } } }`.
- Publishing/signing is wired to environment variables:
  - Signing: `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`.
  - Publishing: `PUBLISH_TOKEN`.
- Kover is enabled and configured to emit an XML report during `check` (`kover { reports { total { xml { onCheck = true } } } }`).
- Note on config-cache/build-cache:
  - `org.gradle.configuration-cache = true` and `org.gradle.caching = true` are enabled (see `gradle.properties`). If you hit dependency transform cache oddities, a clean + refresh can help:
    - `./gradlew clean --refresh-dependencies test`
- If PluginState was changed, we need to make one build without caches: `./gradlew clean build --no-build-cache`

#### Testing (how tests are structured here)

- Test framework:
  - Unit tests are JUnit 4 (`junit:junit:4.13.2`). Test classes commonly use Kotlin-style `fun test...()` methods and extend IntelliJ test framework base classes.
  - IntelliJ test framework in use: `BasePlatformTestCase`.
  - Python-specific descriptor: `fixtures.MyPlatformTestCase` overrides `getProjectDescriptor()` to return `PyLightProjectDescriptor(LanguageLevel.PYTHON311)`, so light tests run with Python 3.11 language level.

- Important local test infrastructure:
  - `fixtures.TestBase` is the common base class for most plugin tests.
    - Calls `PythonTestSetup.configurePythonHelpers()` **before** `super.setUp()`.
    - Resets `PluginSettingsState` to a known baseline (many feature toggles).
    - Creates and registers a Python mock SDK via `PythonTestSetup.createAndRegisterSdk(...)` using the fixture temp dir root.
    - Enables `PyTypeCheckerInspection`.
  - `fixtures.PythonTestSetup`:
    - Sets system property `idea.python.helpers.path` to: `${PathManager.getHomePath()}/plugins/python-ce/helpers`.
      - This is a frequent source of “works locally but not on CI” issues if the selected IntelliJ distribution doesn’t contain `python-ce` helpers at that path.
    - Uses `PythonMockSdk.create(...)` to create a mock Python SDK and registers disposal cleanup; optionally can add it to `ProjectJdkTable` (for heavy tests).
  - `fixtures.MyPlatformTestCase.getTestDataPath()` is `src/test/testData` (if your tests use `myFixture.configureByFile(...)`, that’s the root).

#### Running tests

- Run the whole test suite:
  - `./gradlew test`

- Run a single test class (recommended for local dev because this suite is large):
  - `./gradlew test --tests com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.populate.PopulateArgumentsLocalTest`

- Run a single test method:
  - `./gradlew test --tests com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.populate.PopulateArgumentsLocalTest.test<MethodName>`

#### Known test status (verified on 2025-12-12)

- ✅ Verified passing example command:
  - `./gradlew test --tests com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.populate.PopulateArgumentsLocalTest`
  - Run focused tests while developing, and only run the full suite once those failing groups are addressed.

#### Adding a new test (project conventions)

- Prefer extending `fixtures.TestBase` so you automatically get:
  - Python helpers path configured (`idea.python.helpers.path`).
  - Deterministic `PluginSettingsState` baseline.
  - A registered mock Python SDK.
  - `PyTypeCheckerInspection` enabled.

- Typical pattern for intention tests (used throughout `src/test/kotlin/.../intention/...`):
  - Use `myFixture.configureByText("a.py", """ ... """)`.
  - Place `<caret>` in the source to control caret position.
  - Execute an intention/action with `myFixture.launchAction(SomeIntention())`.
  - Verify output with `myFixture.checkResult(expected.trimIndent())`.

- If your test needs additional source roots:
  - Use `runWithSourceRoots(...)` from `fixtures.MyPlatformTestCase` to temporarily add/remove roots via `PsiTestUtil.addSourceRoot`.

#### Development / debugging notes specific to this repo

- Settings toggles are central:
  - Many features/intentions/inspections are gated behind `PluginSettingsState` flags. If an intention appears “missing” in tests, verify the corresponding `enable...` flag is `true` (and remember `fixtures.TestBase` resets them in `setUp()`).

- Python platform dependency:
  - Because this plugin targets `platformType=PY` and depends on Python infrastructure, tests assume Python plugin components are present. The helpers path is hardcoded to `plugins/python-ce/helpers` under the selected IDE home.

- Test data root:
  - `src/test/testData` is the default test-data path (via `MyPlatformTestCase`).

- UI-test run configuration:
  - `runIdeForUiTests` injects Robot server plugin and sets JVM args; keep `-Drobot-server.port=8082` in mind if you have port conflicts.

- DO NOT read files from the `spec/` folder unless instructed to do so.