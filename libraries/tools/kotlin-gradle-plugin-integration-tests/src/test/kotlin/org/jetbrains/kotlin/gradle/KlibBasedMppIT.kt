/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.gradle.api.logging.LogLevel
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.util.modify
import org.jetbrains.kotlin.konan.target.HostManager
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@MppGradlePluginTests
class KlibBasedMppIT : KGPBaseTest() {
    companion object {
        private const val MODULE_GROUP = "com.example"
    }

    override val defaultBuildOptions: BuildOptions
        get() = super.defaultBuildOptions.copy(logLevel = LogLevel.DEBUG)

    @GradleTestWithOsCondition(enabledForCI = [OS.LINUX, OS.MAC])
    fun testBuildWithProjectDependency(gradleVersion: GradleVersion) {
        testBuildWithDependency(gradleVersion) {
            gradleBuildScript().appendText(
                "\n" + """
            dependencies {
                commonMainImplementation(project("$dependencyModuleName"))
            }
        """.trimIndent()
            )
        }
    }

    @GradleTestWithOsCondition(enabledForCI = [OS.LINUX, OS.MAC])
    fun testPublishingAndConsumptionWithEmptySourceSet(gradleVersion: GradleVersion) {
        testBuildWithDependency(gradleVersion) {
            // KT-36674
            projectDir.resolve("$dependencyModuleName/src/$hostSpecificSourceSet").run {
                assertTrue { isDirectory }
                deleteRecursively()
            }
            publishProjectDepAndAddDependency(validateHostSpecificPublication = false)
        }
    }

    @GradleTestWithOsCondition(enabledForCI = [OS.LINUX, OS.MAC])
    fun testHostSpecificSourceSetsInTransitiveDependencies(gradleVersion: GradleVersion) {
        project("common-klib-lib-and-app", gradleVersion) {
            // KT-41083
            // Publish a lib with host specific source sets depending on another lib with host-specific source sets
            val projectDepName = "dependency"
            val publishedGroup = "published"
            val producerProjectName = "producer"
            embedProject(this, this.projectName, renameTo = projectDepName)
            projectDir.resolve("$projectDepName/src").walkTopDown().filter { it.extension == "kt" }.forEach { ktFile ->
                // Avoid FQN duplicates between producer & consumer
                ktFile.modify { it.replace("package com.h0tk3y.hmpp.klib.demo", "package com.h0tk3y.hmpp.klib.lib") }
            }

            gradleBuildScript(projectDepName).appendText(
                """
            ${"\n"}
            group = "$publishedGroup"
            """.trimIndent()
            )
            gradleBuildScript().modify {
                transformBuildScriptWithPluginsDsl(it) +
                        """
                    ${"\n"}
                    dependencies { "commonMainImplementation"(project(":$projectDepName")) }
                    group = "$publishedGroup"
                    """.trimIndent()
            }
            gradleSettingsScript().appendText("\nrootProject.name = \"$producerProjectName\"")

            build("publish")

            // Then consume the published project. To do that, rename the modules so that Gradle chooses the published ones given the original
            // Maven coordinates and doesn't resolve them as project dependencies.

            val localGroup = "local"
            gradleBuildScript(projectDepName).appendText("""${"\n"}group = "$localGroup"""")
            gradleBuildScript().appendText(
                """
            ${"\n"}
            repositories { maven("${'$'}rootDir/repo") }
            dependencies { "commonMainImplementation"("$publishedGroup:$producerProjectName:1.0") }
            group = "$localGroup"
            """.trimIndent()
            )

            // The consumer should correctly receive the klibs of the host-specific source sets

            checkTaskCompileClasspath(
                "compile${hostSpecificSourceSet.capitalize()}KotlinMetadata",
                listOf(
                    "published-producer-$hostSpecificSourceSet.klib",
                    "published-producer-commonMain.klib",
                    "published-dependency-$hostSpecificSourceSet.klib",
                    "published-dependency-commonMain.klib"
                ),
                isNative = true
            )
        }
    }

    @GradleTestWithOsCondition(enabledForCI = [OS.LINUX, OS.MAC])
    fun testBuildWithPublishedDependency(gradleVersion: GradleVersion) {
        testBuildWithDependency(gradleVersion) {
            publishProjectDepAndAddDependency(validateHostSpecificPublication = true)
        }
    }

    private fun TestProject.publishProjectDepAndAddDependency(validateHostSpecificPublication: Boolean) {
        build(":$dependencyModuleName:publish") {
            if (validateHostSpecificPublication)
                checkPublishedHostSpecificMetadata(this@build)
        }

        gradleBuildScript().appendText(
            "\n" + """
            repositories {
                maven("${'$'}rootDir/repo")
            }
            dependencies {
                commonMainImplementation("$MODULE_GROUP:$dependencyModuleName:1.0")
            }
        """.trimIndent()
        )

        // prevent Gradle from linking the above dependency to the project:
        gradleBuildScript(dependencyModuleName).appendText("\ngroup = \"some.other.group\"")
    }

    private val dependencyModuleName = "project-dep"

    private fun testBuildWithDependency(gradleVersion: GradleVersion, configureDependency: TestProject.() -> Unit) =
        project("common-klib-lib-and-app", gradleVersion) {
            embedProject(this, "common-klib-lib-and-app", renameTo = dependencyModuleName)

            projectDir.resolve("$dependencyModuleName/src/commonMain/kotlin/TestKt37832.kt").writeText(
                "package com.example.test.kt37832" + "\n" + "class MyException : RuntimeException()"
            )

            gradleBuildScript().modify(::transformBuildScriptWithPluginsDsl)

            projectDir.resolve(dependencyModuleName + "/src").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                file.modify { it.replace("package com.h0tk3y.hmpp.klib.demo", "package com.projectdep") }
            }

            configureDependency()

            projectDir.resolve("src/commonMain/kotlin/LibUsage.kt").appendText(
                "\n" + """
            package com.h0tk3y.hmpp.klib.demo.test
            
            import com.projectdep.LibCommonMainExpect as ProjectDepExpect
            
            private fun useProjectDep() {
                ProjectDepExpect()
            }
        """.trimIndent()
            )

            projectDir.resolve("src/linuxMain/kotlin/LibLinuxMainUsage.kt").appendText(
                "\n" + """
            package com.h0tk3y.hmpp.klib.demo.test
            
            import com.projectdep.libLinuxMainFun as libFun
            
            private fun useProjectDep() {
                libFun()
            }
        """.trimIndent()
            )

            val tasksToExecute = listOf(
                ":compileJvmAndJsMainKotlinMetadata",
                ":compileLinuxMainKotlinMetadata",
                ":compile${hostSpecificSourceSet.capitalize()}KotlinMetadata"
            )

            build("assemble") {

                assertTasksExecuted(*tasksToExecute.toTypedArray())

                assertFileInProjectExists("build/classes/kotlin/metadata/commonMain/default/manifest")
                assertFileInProjectExists("build/classes/kotlin/metadata/jvmAndJsMain/default/manifest")
                assertFileInProjectExists("build/classes/kotlin/metadata/linuxMain/klib/${projectName}_linuxMain.klib")

                // Check that the common and JVM+JS source sets don't receive the Kotlin/Native stdlib in the classpath:
                run {
                    fun getClasspath(taskPath: String): Iterable<String> {
                        val argsPrefix = " $taskPath Kotlin compiler args:"
                        return output.lines().single { argsPrefix in it }
                            .substringAfter("-classpath ").substringBefore(" -").split(File.pathSeparator)
                    }

                    fun classpathHasKNStdlib(classpath: Iterable<String>) = classpath.any { "klib/common/stdlib" in it.replace("\\", "/") }

                    assertFalse(classpathHasKNStdlib(getClasspath(":compileCommonMainKotlinMetadata")))
                    assertFalse(classpathHasKNStdlib(getClasspath(":compileJvmAndJsMainKotlinMetadata")))
                }
            }
        }

    private val hostSpecificSourceSet = when {
        HostManager.hostIsMac -> "iosMain"
        HostManager.hostIsLinux -> "embeddedMain"
        HostManager.hostIsMingw -> "windowsMain"
        else -> error("unexpected host")
    }

    private fun TestProject.checkPublishedHostSpecificMetadata(buildResult: BuildResult) = with(buildResult) {
        val groupDir = projectDir.resolve("repo/com/example")

        assertTasksExecuted(":$dependencyModuleName:compile${hostSpecificSourceSet.capitalize()}KotlinMetadata")

        // Check that the metadata JAR doesn't contain the host-specific source set entries, but contains the shared-Native source set
        // that can be built on every host:

        ZipFile(groupDir.resolve("$dependencyModuleName/1.0/$dependencyModuleName-1.0-all.jar")).use { metadataJar ->
            assertTrue { metadataJar.entries().asSequence().none { it.name.startsWith(hostSpecificSourceSet) } }
            assertTrue { metadataJar.entries().asSequence().any { it.name.startsWith("linuxMain") } }
        }

        // Then check that in the host-specific modules, there's a metadata artifact that contains the host-specific source set but not the
        // common source sets:

        val hostSpecificTargets = when {
            HostManager.hostIsMac -> listOf("iosArm64", "iosX64")
            HostManager.hostIsLinux -> listOf("linuxMips32", "linuxMipsel32")
            HostManager.hostIsMingw -> listOf("mingwX64", "mingwX86")
            else -> error("unexpected host")
        }

        hostSpecificTargets.forEach { targetName ->
            val moduleName = "$dependencyModuleName-${targetName.toLowerCase()}"
            ZipFile(groupDir.resolve("$moduleName/1.0/$moduleName-1.0-metadata.jar")).use { metadataJar ->
                assertTrue { metadataJar.entries().asSequence().any { it.name.startsWith(hostSpecificSourceSet) } }
                assertTrue { metadataJar.entries().asSequence().none { it.name.startsWith("commonMain") } }
            }
        }

        // Also check that the targets that don't include any host-specific sources don't even have the metadata artifact:

        groupDir.resolve("$dependencyModuleName-linuxx64/1.0/$dependencyModuleName-linuxx64-1.0-metadata.jar").let { metadataJar ->
            assertTrue { !metadataJar.exists() }
        }
    }

    private val transitiveDepModuleName = "transitive-dep"

    @GradleTestWithOsCondition(enabledForCI = [OS.LINUX, OS.MAC])
    fun testKotlinNativeImplPublishedDeps(gradleVersion: GradleVersion) {
        testKotlinNativeImplementationDependencies(gradleVersion) {
            build(":$transitiveDepModuleName:publish", ":$dependencyModuleName:publish")

            gradleBuildScript().appendText(
                "\n" + """
                repositories {
                    maven("${'$'}rootDir/repo")
                }
                dependencies {
                    commonMainImplementation("$MODULE_GROUP:$dependencyModuleName:1.0")
                }
                """.trimIndent()
            )

            listOf(transitiveDepModuleName, dependencyModuleName).forEach {
                // prevent Gradle from linking the above dependency to the project:
                gradleBuildScript(it).appendText("\ngroup = \"com.some.other.group\"")
            }
        }
    }

    @GradleTestWithOsCondition(enabledForCI = [OS.LINUX, OS.MAC])
    fun testKotlinNativeImplProjectDeps(gradleVersion: GradleVersion) {
        testKotlinNativeImplementationDependencies(gradleVersion) {
            gradleBuildScript().appendText("\ndependencies { \"commonMainImplementation\"(project(\":$dependencyModuleName\")) }")
        }
    }

    private fun testKotlinNativeImplementationDependencies(
        gradleVersion: GradleVersion,
        setupDependencies: TestProject.() -> Unit
    ) = project("common-klib-lib-and-app", gradleVersion) {
        embedProject(this, "common-klib-lib-and-app", renameTo = transitiveDepModuleName)
        embedProject(this, "common-klib-lib-and-app", renameTo = dependencyModuleName).apply {
            projectDir.resolve(dependencyModuleName).walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                // Avoid duplicate FQNs as in the compatibility mode, the K2Metadata compiler reports duplicate symbols on them:
                file.modify { it.replace("package com.h0tk3y.hmpp.klib.demo", "package com.h0tk3y.hmpp.klib.demo1") }
            }
        }
        gradleBuildScript().modify(::transformBuildScriptWithPluginsDsl)
        gradleBuildScript(dependencyModuleName).appendText("\ndependencies { \"commonMainImplementation\"(project(\":$transitiveDepModuleName\")) }")

        setupDependencies(this)

        val compileNativeMetadataTaskName = "compileLinuxMainKotlinMetadata"
        build(":$compileNativeMetadataTaskName")
    }

    @GradleTestWithOsCondition(enabledForCI = [OS.LINUX, OS.MAC])
    fun testAvoidSkippingSharedNativeSourceSetKt38746(gradleVersion: GradleVersion) {
        project("hierarchical-all-native", gradleVersion) {
            val targetNames = listOf(
                // Try different alphabetical ordering of the targets to ensure that the behavior doesn't depend on it, as with 'first target'
                listOf("a1", "a2", "a3"),
                listOf("a3", "a1", "a2"),
                listOf("a2", "a3", "a1"),
            )
            val targetParamNames = listOf("mingwTargetName", "linuxTargetName", "macosTargetName", "currentHostTargetName")
            for (names in targetNames) {
                val currentHostTargetName = when {
                    HostManager.hostIsMingw -> names[0]
                    HostManager.hostIsLinux -> names[1]
                    HostManager.hostIsMac -> names[2]
                    else -> error("unexpected host")
                }
                val params = targetParamNames.zip(names + currentHostTargetName) { k, v -> "-P$k=$v" }
                build(":clean", ":compileCurrentHostAndLinuxKotlinMetadata", *params.toTypedArray()) {
                    assertTasksExecuted(":compileCurrentHostAndLinuxKotlinMetadata", ":compileAllNativeKotlinMetadata")
                }
            }
        }
    }
}