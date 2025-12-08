package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase
import fixtures.doIntentionTest
import fixtures.withPopulatePopupSelection

class PopulateDecoratorTest : TestBase() {

    fun testDecoratorArgumentsPopulation() {
        withPopulatePopupSelection(index = 0) {
            myFixture.doIntentionTest(
                "a.py",
                """
                from typing import Callable

                def decorate(arg1) -> Callable:
                    def wrapper(func) -> Callable:
                        return func

                    return wrapper


                @decorate(<caret>)
                def foo():
                    pass
                """,
                """
                from typing import Callable

                def decorate(arg1) -> Callable:
                    def wrapper(func) -> Callable:
                        return func

                    return wrapper


                @decorate(arg1=...)
                def foo():
                    pass
                """,
                "Populate arguments..."
            )
        }
    }
}
