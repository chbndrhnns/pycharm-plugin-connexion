package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertRefactoringActionAvailable

class IpAddressTest : TestBase() {

    private val actionId = "com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.IntroduceCustomTypeRefactoringAction"

    fun testIPv4Address_IntentionAvailable() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            import ipaddress
            
            def f(ip: ipaddress.IPv4A<caret>ddress):
                pass
            """,
            actionId
        )
    }

    fun testIPv4Network_IntentionAvailable() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            import ipaddress
            
            def f(net: ipaddress.IPv4N<caret>etwork):
                pass
            """,
            actionId
        )
    }

    fun testPath_IntentionAvailable() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            from pathlib import Path
            
            def f(p: Pa<caret>th):
                pass
            """,
            actionId
        )
    }
}
