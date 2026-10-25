// 版本对齐 backdrop 2.0.1 的构建环境（AndroidLiquidGlass-kmp / gradle/libs.versions.toml）：
// AGP 9.3.2（内置 Kotlin 支持，无需 org.jetbrains.kotlin.android）/ Kotlin 2.4.10 / CMP 1.12.0
plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.12.0" apply false
}
