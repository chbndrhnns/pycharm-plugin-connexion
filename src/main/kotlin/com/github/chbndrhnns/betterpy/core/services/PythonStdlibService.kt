package com.github.chbndrhnns.betterpy.core.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.psi.LanguageLevel

@Service(Service.Level.PROJECT)
class PythonStdlibService(private val project: Project) {

    private val stdlibCache = mutableMapOf<String, Set<String>>()

    fun isStdlibModule(name: String, sdk: Sdk?): Boolean {
        val stdlibModules = getStdlibModules(sdk)
        return stdlibModules.contains(name)
    }

    fun getStdlibModules(sdk: Sdk?): Set<String> {
        val actualSdk = sdk ?: com.intellij.openapi.module.ModuleManager.getInstance(project)
            .modules
            .firstNotNullOfOrNull { com.jetbrains.python.sdk.PythonSdkUtil.findPythonSdk(it) }
        ?: return emptySet()
        val versionString = actualSdk.versionString ?: return emptySet()

        return stdlibCache.getOrPut(versionString) {
            fetchStdlibModules(actualSdk)
        }
    }

    private fun fetchStdlibModules(sdk: Sdk): Set<String> {
        val languageLevel = LanguageLevel.fromPythonVersion(sdk.versionString ?: "")
            ?: LanguageLevel.getDefault()
        // For now, use a static list or a simple heuristic.
        // In a real PyCharm environment, we could query the SDK or use existing indices.
        // Here we'll provide a decent list for common Python versions.
        return getBaseStdlibModules(languageLevel)
    }

    private fun getBaseStdlibModules(level: LanguageLevel): Set<String> {
        val modules = mutableSetOf(
            "abc", "argparse", "ast", "asyncio", "base64", "collections", "concurrent", "contextlib", "csv",
            "datetime", "decimal", "enum", "functools", "glob", "gzip", "hashlib", "html", "http", "importlib",
            "inspect", "io", "itertools", "json", "logging", "math", "multiprocessing", "os", "pathlib", "pickle",
            "random", "re", "shutil", "socket", "sqlite3", "ssl", "statistics", "string", "subprocess", "sys",
            "tempfile", "threading", "time", "typing", "unittest", "urllib", "uuid", "xml", "zipfile"
        )
        if (level.isAtLeast(LanguageLevel.PYTHON37)) {
            modules.add("dataclasses")
        }
        if (level.isAtLeast(LanguageLevel.PYTHON39)) {
            modules.add("zoneinfo")
            modules.add("graphlib")
        }
        return modules
    }

    companion object {
        fun getInstance(project: Project): PythonStdlibService = project.service()
    }
}
