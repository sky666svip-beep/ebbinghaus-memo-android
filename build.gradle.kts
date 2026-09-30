// 根构建脚本：集中声明插件版本，子模块按需 apply
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.test") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("androidx.baselineprofile") version "1.3.3" apply false
}
