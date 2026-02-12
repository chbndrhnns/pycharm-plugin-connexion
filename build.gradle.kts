import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

fun computeDevVersion(baseVersion: String): String {
    val branch = providers.exec {
        commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
    }.standardOutput.asText.get().trim().replace(Regex("[^a-zA-Z0-9._-]"), "-")
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    return "$baseVersion+$branch.$timestamp"
}

if (gradle.startParameter.taskNames.any { it.contains("buildPlugin") }) {
    version = computeDevVersion(version.toString())
}
// set in ~/.gradle/gradle.properties, e.g. `/Users/me/Applications/PyCharm X.app/Contents`
val localIdePath = project.findProperty("localIdePath") as? String

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.jetbrains.intellij.java" && requested.name == "java-compiler-ant-tasks") {
            useVersion("253.29346.308")
        }
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
    testImplementation(libs.json.schema.validator)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.jackson.dataformat.yaml)
    compileOnly(libs.jackson.module.kotlin)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
            useInstaller = false
        )

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // Module Dependencies. Uses `platformBundledModules` property from the gradle.properties file for bundled IntelliJ Platform modules.
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

        testFramework(TestFrameworkType.Platform)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    // Only build searchable options if the 'publishPlugin' task is explicitly requested
    buildSearchableOptions = gradle.startParameter.taskNames.none {
        it.contains("runIde") || it.contains("runIdeForUiTests") || it.contains("test")
    }

    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = provider { project.version.toString() }

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion")
            .map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
    // Support CalVer format: YYYY.MM.PATCH or YYYY.MM.PATCH-channel.build (two-digit month)
    // Include Unreleased so getChangelog --unreleased works.
    headerParserRegex.set("""(Unreleased|\d{4}\.\d{2}\.\d+(-\w+\.\w+)?)""".toRegex())
    version.set(providers.gradleProperty("pluginVersion"))
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }

    // Exclude documentation generation tests from normal test runs unless explicitly requested
    test {
        if (!project.hasProperty("runDocGenTest")) {
            exclude("**/GenerateFeatureDocsTest.class")
        }
    }
}

// Custom task to generate feature documentation
// This runs the GenerateFeatureDocsTest which requires IntelliJ Platform test infrastructure
tasks.register<Exec>("generateFeatureDocs") {
    description = "Generates feature documentation from @Feature annotations"
    group = "documentation"

    workingDir = projectDir
    commandLine(
        "./gradlew", "test",
        "--tests", "com.github.chbndrhnns.betterpy.featureflags.GenerateFeatureDocsTest",
        "-PrunDocGenTest=true"
    )
}

// runIdeForUiTests task for UI testing with Robot Server Plugin
val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    task {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Drobot-server.port=8082",
                "-Dide.mac.message.dialogs.as.sheets=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false",
            )
        }
    }

    plugins {
        robotServerPlugin()
    }
}

// runLocalIde uses local IDE installation from localIdePath property (set in ~/.gradle/gradle.properties)
// Uses the user's actual IDE configuration instead of an isolated sandbox
val runLocalIde by intellijPlatformTesting.runIde.registering {
    if (localIdePath != null) {
        localPath = file(localIdePath)
    }

    splitMode = false
    splitModeTarget = org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.SplitModeTarget.BOTH

    task {
        maxHeapSize = "2g"
        jvmArgs("-Dignore.ide.script.launcher.used=true")
        jvmArgs("-Dide.slow.operations.assertion=true")
        jvmArgs("-Didea.is.internal=true")
        jvmArgs(
            "-XX:+HeapDumpOnOutOfMemoryError",
            "-XX:HeapDumpPath=${rootProject.projectDir}/build/java_error_in_idea64.hprof",
            "-XX:ErrorFile=${rootProject.projectDir}/build/java_error_in_idea64.log"
        )
        jvmArgs("-Didea.logger.exception.expiration.minutes=0")
        args(listOf("nosplash"))
    }
}
