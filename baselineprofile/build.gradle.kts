// :baselineprofile —— Macrobenchmark 模块（com.android.test，targetProjectPath=:app）
//
// 用途：录制冷启动关键路径，生成 app/src/main/baseline-prof.txt。
// 录制强依赖真机/模拟器；本环境无设备，故基线 profile 以手写最小集提交，
// 有设备时执行：
//   ./gradlew :app:generateBaselineProfile   （或 :baselineprofile:pmp）
// 详见 design/IMPLEMENTATION_SUMMARY_V2.md。
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.ebbinghaus.memo.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
}
