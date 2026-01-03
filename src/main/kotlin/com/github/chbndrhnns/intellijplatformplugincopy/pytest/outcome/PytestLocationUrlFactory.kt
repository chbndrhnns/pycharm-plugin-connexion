package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.jetbrains.python.psi.PyFunction

object PytestLocationUrlFactory {
    fun fromPyFunction(function: PyFunction): String? {
        val qName = function.qualifiedName ?: return null
        val virtualFile = function.containingFile.virtualFile ?: return null

        // SMTestProxy.locationUrl uses the parent directory path in angle brackets
        // and prefixes the qName with the parent directory name (module name)
        // e.g., python</path/to/tests>://tests.test_file.test_func
        val parentDir = virtualFile.parent ?: return null
        val parentDirPath = parentDir.path
        val parentDirName = parentDir.name

        // Prepend the parent directory name to the qualified name
        val fullQName = "$parentDirName.$qName"
        return "python<$parentDirPath>://$fullQName"
    }
}
