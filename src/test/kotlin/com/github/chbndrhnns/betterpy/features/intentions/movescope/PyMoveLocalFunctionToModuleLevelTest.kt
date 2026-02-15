package com.github.chbndrhnns.betterpy.features.intentions.movescope

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class PyMoveLocalFunctionToModuleLevelTest : TestBase() {

    private val intentionName = "BetterPy: Move to outer scope"

    // U3: Local function → module level (no captures) — clean move, call sites unchanged
    fun testLocalFunctionNoCaptures() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                def outer():
                    def inn<caret>er(x):
                        return x + 1
                    return inner(5)
            """,
            after = """
                def outer():
                    return inner(5)

                def inner(x):
                    return x + 1
            """,
            intentionName = intentionName
        )
    }

    // U4: Local function with captured variables → converted to parameters, call sites updated
    fun testLocalFunctionWithCaptures() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                def outer():
                    multiplier = 3
                    def inn<caret>er(x):
                        return x * multiplier
                    return inner(5)
            """,
            after = """
                def outer():
                    multiplier = 3
                    return inner(5, multiplier)

                def inner(x, multiplier):
                    return x * multiplier
            """,
            intentionName = intentionName
        )
    }

    // U5: Local function with `nonlocal` — intention should be blocked
    fun testNotAvailableWithNonlocal() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                def outer():
                    count = 0
                    def inn<caret>er():
                        nonlocal count
                        count += 1
                    inner()
            """,
            intentionName = intentionName
        )
    }

    // E3: Local function returned as closure — intention should be blocked (semantics change)
    fun testNotAvailableWhenReturnedAsClosure() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                def outer():
                    x = 10
                    def inn<caret>er():
                        return x
                    return inner
            """,
            intentionName = intentionName
        )
    }

    // E4: Local function with yield (generator) — must preserve generator nature
    fun testGeneratorLocalFunction() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                def outer():
                    def gen<caret>erator(n):
                        for i in range(n):
                            yield i
                    return list(generator(5))
            """,
            after = """
                def outer():
                    return list(generator(5))

                def generator(n):
                    for i in range(n):
                        yield i
            """,
            intentionName = intentionName
        )
    }

    // E6: async def local function — async must be preserved
    fun testAsyncLocalFunction() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                async def outer():
                    async def inn<caret>er(url):
                        return url
                    return await inner("http://example.com")
            """,
            after = """
                async def outer():
                    return await inner("http://example.com")

                async def inner(url):
                    return url
            """,
            intentionName = intentionName
        )
    }

    // E10: Local function with *args/**kwargs that are closure-captured
    fun testLocalFunctionWithCapturedArgsKwargs() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                def outer(*args, **kwargs):
                    def inn<caret>er():
                        return args, kwargs
                    return inner()
            """,
            after = """
                def outer(*args, **kwargs):
                    return inner(args, kwargs)

                def inner(args, kwargs):
                    return args, kwargs
            """,
            intentionName = intentionName
        )
    }

    // Not available for top-level function
    fun testNotAvailableForTopLevelFunction() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                def top_le<caret>vel():
                    pass
            """,
            intentionName = intentionName
        )
    }

    // Not available when caret is not on function name
    fun testNotAvailableOnBodyOfLocalFunction() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                def outer():
                    def inner():
                        pa<caret>ss
            """,
            intentionName = intentionName
        )
    }

    // Name collision at module level — intention blocked
    fun testNotAvailableWhenNameCollisionAtModuleLevel() {
        myFixture.assertIntentionNotAvailable(
            filename = "a.py",
            text = """
                def inner():
                    pass

                def outer():
                    def inn<caret>er():
                        pass
            """,
            intentionName = intentionName
        )
    }

    // Multiple captures from enclosing scope
    fun testLocalFunctionWithMultipleCaptures() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                def outer():
                    a = 1
                    b = 2
                    def inn<caret>er(x):
                        return x + a + b
                    return inner(3)
            """,
            after = """
                def outer():
                    a = 1
                    b = 2
                    return inner(3, a, b)

                def inner(x, a, b):
                    return x + a + b
            """,
            intentionName = intentionName
        )
    }

    // Local function with decorators
    fun testDecoratedLocalFunction() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                import functools

                def outer():
                    @functools.lru_cache
                    def inn<caret>er(x):
                        return x * 2
                    return inner(5)
            """,
            after = """
                import functools

                def outer():
                    return inner(5)

                @functools.lru_cache
                def inner(x):
                    return x * 2
            """,
            intentionName = intentionName
        )
    }

    // Local function with captured parameter from outer function
    fun testLocalFunctionCapturesOuterParameter() {
        myFixture.doIntentionTest(
            filename = "a.py",
            before = """
                def outer(factor):
                    def inn<caret>er(x):
                        return x * factor
                    return inner(5)
            """,
            after = """
                def outer(factor):
                    return inner(5, factor)

                def inner(x, factor):
                    return x * factor
            """,
            intentionName = intentionName
        )
    }
}
