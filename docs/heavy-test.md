### Short answer

You **cannot** properly test cross‑module symbol resolution with `BasePlatformTestCase` + `LightProjectDescriptor`.
Light tests only give you a single “light” module. For real cross‑module resolution via PSI you need a **heavy** project
setup with multiple real modules and dependencies, i.e. switch away from `LightProjectDescriptor` and use a heavy
fixture (`HeavyPlatformTestCase` / `IdeaProjectTestFixture` + module fixtures).

---

### Why it doesn’t work with `BasePlatformTestCase` + `LightProjectDescriptor`

`BasePlatformTestCase` uses a light project under the hood:

- The light infrastructure creates **one synthetic module**.
- `LightProjectDescriptor` lets you tweak that module a bit (SDK, content roots, etc.), but does **not** support
  creating a proper graph of multiple modules with dependencies between them.
- Cross‑module PSI resolution (e.g. “module A references symbol defined in module B”) relies on real project model
  entities: multiple `Module`s, `OrderEntry` dependencies, separate content/source roots, etc.

Because these things are missing in a light project, reference resolution logic that depends on module boundaries simply
never sees the setup you want to test.

So with `BasePlatformTestCase` + `LightProjectDescriptor` you can only test:

- resolution *within* that one module
- or resolution which is intentionally module‑agnostic

but not true **cross‑module** scenarios.

---

### What to use instead (heavy tests)

To test cross‑module PSI resolution, use a **heavy** test fixture that creates real modules:

1. Use a heavy base class, e.g.
    - `HeavyPlatformTestCase`, or
    - a fixture built via `IdeaTestFixtureFactory.createFixtureBuilder()` with module builders (e.g.
      `JavaModuleFixtureBuilder`, or equivalents used in PyCharm tests).

2. In `setUp()` (or in a custom project descriptor for the heavy test infrastructure):
    - Create **two modules**: `moduleA`, `moduleB`.
    - Add content + source roots for each module.
    - Add a **dependency** from `moduleA` to `moduleB` using
      `ModuleRootModificationUtil.addDependency(moduleA, moduleB)` / test utilities like
      `PsiTestUtil.addDependency(...)`.

3. Place your test files into the corresponding modules (e.g. using test utilities to write files under each module’s
   source root) and then:
    - obtain PSI files for them
    - run your resolution logic / `Reference.resolve()`
    - assert that the target element comes from the *other* module.

Once you have real modules & dependencies, PSI resolution behaves like in the IDE, and cross‑module tests will work as
expected.

---

### Practical takeaway

If your goal is:

- “Given a symbol in module A, resolve it to a declaration in module B”

then with the current setup:

- **BasePlatformTestCase + LightProjectDescriptor** → **insufficient**, cannot model multiple modules; cross‑module
  resolution won’t be testable.
- **Heavy project fixture with multiple modules** → **correct approach**, PSI will respect module dependencies and your
  cross‑module resolution tests will pass.

So the way to “achieve cross‑module resolution via PSI” is not to tweak `LightProjectDescriptor`, but to **switch the
test base to a heavy fixture that supports multiple modules.**