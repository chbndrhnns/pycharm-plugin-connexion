// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetbrains.python.fixtures

import com.jetbrains.python.PythonHelpersLocator


object PythonTestUtil {
    val testDataPath: String
        get() = System.getProperty("user.dir") + "/testData"
}