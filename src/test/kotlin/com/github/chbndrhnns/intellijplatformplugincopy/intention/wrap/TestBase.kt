package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.MyPlatformTestCase
import com.jetbrains.python.inspections.PyTypeCheckerInspection

abstract class TestBase : MyPlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(PyTypeCheckerInspection::class.java)
    }
}