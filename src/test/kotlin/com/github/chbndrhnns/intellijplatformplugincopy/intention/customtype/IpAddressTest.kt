package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertIntentionAvailable

class IpAddressTest : TestBase() {

    fun testIPv4Address_IntentionAvailable() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            import ipaddress
            
            def f(ip: ipaddress.IPv4A<caret>ddress):
                pass
            """,
            "Introduce custom type from IPv4Address"
        )
    }

    fun testIPv4Network_IntentionAvailable() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            import ipaddress
            
            def f(net: ipaddress.IPv4N<caret>etwork):
                pass
            """,
            "Introduce custom type from IPv4Network"
        )
    }

    fun testPath_IntentionAvailable() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            from pathlib import Path
            
            def f(p: Pa<caret>th):
                pass
            """,
            "Introduce custom type from Path"
        )
    }
}
