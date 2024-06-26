import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "com.hsc.compiler"
version = "1.1.0"

repositories {
    mavenCentral()
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {

    jvm {
        withJava()
        mainRun {
            args("../examples/simple/src/example.hsl --mode=optimize --house-name=\"simple-example\" --color=always --target=htsl --output ../examples/simple/build --slash-idents".split(" "))
            mainClass.set("com.hsc.mason.MainKt")
        }
    }
    mingwX64 {
        binaries {
            executable(listOf(NativeBuildType.RELEASE)) {
                baseName = "hsc"
                entryPoint = "com.hsc.compiler.main"
            }
        }
    }
    linuxX64 {
        binaries {
            executable(listOf(NativeBuildType.RELEASE)) {
                baseName = "hsc"
                entryPoint = "com.hsc.compiler.main"
            }
        }
    }
    macosX64 {
        binaries {
            executable(listOf(NativeBuildType.RELEASE)) {
                baseName = "hsc"
                entryPoint = "com.hsc.compiler.main"
            }
        }
    }
    macosArm64 {
        binaries {
            executable(listOf(NativeBuildType.RELEASE)) {
                baseName = "hsc"
                entryPoint = "com.hsc.compiler.main"
            }
        }
    }

    sourceSets.all {
        languageSettings {
            languageVersion = "2.0"
        }
    }

    sourceSets {
        // Kotlin Multiplatform? More like: Kotlin other-people-Multi and I freeload platform!!!!
        val commonMain by sourceSets.getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0-RC.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.4.0")
                implementation("com.github.ajalt.clikt:clikt:4.2.2")
                implementation("com.github.ajalt.mordant:mordant:2.4.0")
                implementation("com.github.ajalt.mordant:mordant-coroutines:2.4.0")
                implementation("net.benwoodworth.knbt:knbt:0.11.5")
            }
        }

        val commonTest by sourceSets.getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by sourceSets.getting {

        }

        val posixMain by sourceSets.creating {
            dependsOn(commonMain)
        }
        val mingwX64Main by getting {
            // cannot depend on posixMain thanks to _popen! :-)
        }
        val linuxX64Main by getting {
            dependsOn(posixMain)
        }
        val macosX64Main by getting {
            dependsOn(posixMain)
        }
        val macosArm64Main by getting {
            dependsOn(posixMain)
        }
    }

    tasks.withType<Jar> {
        doFirst {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            val main by kotlin.jvm().compilations.getting
            manifest {
                attributes(
                    "Main-Class" to "com.hsc.compiler.MainKt",
                )
            }
            from({
                main.runtimeDependencyFiles.files.filter { it.name.endsWith("jar") }.map { zipTree(it) }
            })
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xbackend-threads=0", // Multi-threaded compilation 😎
                "-Xcontext-receivers"
            )
        }
    }

}