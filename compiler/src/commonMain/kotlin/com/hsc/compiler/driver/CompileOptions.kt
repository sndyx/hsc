package com.hsc.compiler.driver

import kotlinx.io.files.Path

data class CompileOptions(
    val target: Target,
    val mode: Mode,
    val emitter: EmitterType,
    val output: Path,
)

enum class Target(val label: String) {
    Default("default"),
    HousingPlus("housing+");
}

enum class Mode(val label: String) {
    Strict("strict"),
    Normal("normal"),
    Optimized("optimized");
}

enum class EmitterType(val label: String) {
    Terminal("terminal"),
    Minecraft("minecraft"),
    Internal("internal"),
    Webview("webview"),
}