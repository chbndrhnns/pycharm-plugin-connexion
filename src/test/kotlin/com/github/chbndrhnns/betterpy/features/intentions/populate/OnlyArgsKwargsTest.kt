package com.github.chbndrhnns.betterpy.features.intentions.populate

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class OnlyArgsKwargsTest : TestBase() {

    fun testOnlyArgs_NoPopulateOffered() {
        myFixture.addFileToProject("other.py", "def foo(*items: int): pass")
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from other import foo
            foo(<caret>)
            """,
            "Populate missing arguments with '...'"
        )
    }

    fun testOnlyKwargs_NoPopulateOffered() {
        myFixture.addFileToProject("other.py", "def foo(**kwargs: str): pass")
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from other import foo
            foo(<caret>)
            """,
            "Populate missing arguments with '...'"
        )
    }

    fun testOnlyArgsAndKwargs_NoPopulateOffered() {
        myFixture.addFileToProject("other.py", "def foo(*args, **kwargs): pass")
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from other import foo
            foo(<caret>)
            """,
            "Populate missing arguments with '...'"
        )
    }

    fun testOnlyArgs_NoPopulateRequiredOffered() {
        myFixture.addFileToProject("other.py", "def foo(*args): pass")
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from other import foo
            foo(<caret>)
            """,
            "Populate required arguments with '...'"
        )
    }

    fun testOnlyKwargs_NoPopulateRequiredOffered() {
        myFixture.addFileToProject("other.py", "def foo(**kwargs): pass")
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from other import foo
            foo(<caret>)
            """,
            "Populate required arguments with '...'"
        )
    }

    fun testOnlyArgsAndKwargs_NoPopulateRequiredOffered() {
        myFixture.addFileToProject("other.py", "def foo(*args, **kwargs): pass")
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from other import foo
            foo(<caret>)
            """,
            "Populate required arguments with '...'"
        )
    }

    fun testOnlyArgs_NoPopulateRecursiveOffered() {
        myFixture.addFileToProject("other.py", "def foo(*args): pass")
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from other import foo
            foo(<caret>)
            """,
            "Populate missing arguments recursively with '...'"
        )
    }

    fun testOnlyKwargs_NoPopulateRecursiveOffered() {
        myFixture.addFileToProject("other.py", "def foo(**kwargs): pass")
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from other import foo
            foo(<caret>)
            """,
            "Populate missing arguments recursively with '...'"
        )
    }

    fun testOnlyArgsAndKwargs_NoPopulateRecursiveOffered() {
        myFixture.addFileToProject("other.py", "def foo(*args, **kwargs): pass")
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from other import foo
            foo(<caret>)
            """,
            "Populate missing arguments recursively with '...'"
        )
    }

    fun testStubFile_NoPopulateOffered() {
        myFixture.addFileToProject("other.pyi", "def foo(*args: int, **kwargs: int) -> None: ...")
        myFixture.addFileToProject("other.py", "def foo(*args, **kwargs): pass")
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from other import foo
            foo(<caret>)
            """,
            "Populate missing arguments with '...'"
        )
    }

    fun testParamSpec_NoPopulateOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import ParamSpec
            P = ParamSpec("P")
            def foo(*args: P.args, **kwargs: P.kwargs):
                pass

            foo(<caret>)
            """,
            "Populate missing arguments with '...'"
        )
    }
}
