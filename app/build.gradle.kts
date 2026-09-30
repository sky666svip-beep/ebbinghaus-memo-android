plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.ebbinghaus.memo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ebbinghaus.memo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // 开启 R8 代码混淆 + 资源裁剪，剥离未使用的图标（material-icons-extended）等资源
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 临时复用 debug 签名，便于将 release 包安装到模拟器做真机验证
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            // 统一包名：不再追加 .debug 后缀，debug 与 release 同为 com.ebbinghaus.memo
            // （取自 defaultConfig.applicationId）→ 二者互为覆盖安装：同一份数据、只留一个图标。
            // 代价：同一台设备同一时刻只能装一个变体；若将来 release 改用正式签名，
            //       则与 debug 签名不一致，覆盖安装会因签名校验失败而失败。
            isDebuggable = true
            // 开启单元测试覆盖率采集（AGP 内置 JaCoCo），供 testDebugUnitTestCoverage 使用
            enableUnitTestCoverage = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

/**
 * D 档测试优化：关闭 release 变体的单元测试。
 *
 * 依据 `design/TEST_AUDIT.md` 的实测核实：
 * - `app/src/release/` 源集为空，release 与 debug 的单测**逐条等价**（各 224 条，结果文件一一对应）
 * - R8/资源裁剪**不参与本地单元测试**，故 release 变体单测不产生额外信号
 * - debug 专属的 3 个 Preview 文件（`PreviewFakes`/`PagePreviews` 等）无任何测试引用
 *
 * 收益：`./gradlew test` 从「debug + release 各跑一遍」变为只跑一遍，
 * 冷跑约 92s → 29s（约 −68%）。保留 debug 变体（唯一有意义的单测变体）。
 */
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        variantBuilder.enableUnitTest = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // 纯 Kotlin JVM 领域模块（调度算法 / 档位模型 / 数学文本预处理）
    implementation(project(":core"))

    // AndroidX 与协程
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Jetpack Compose (BOM 统一管理)
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 官方响应式窗口尺寸类别：正确处理折叠屏铰链、多窗口与桌面窗口化，
    // 版本与 compose-bom 2024.10.01（material3 1.3.1）对齐
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Room 数据库持久化
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Baseline Profile 安装器（缺它 profile 不生效）
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Baseline Profile 录制源（com.android.test 模块）；仅在录制时需要设备
    baselineProfile(project(":baselineprofile"))

    // 单元测试（纯 JVM 毫秒级单测）
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // org.json 真实实现：让 ExportCodec 在纯 JVM 单测中可往返；运行时由 Android framework 提供（APK 零字节）
    testImplementation("org.json:json:20240303")

    // Android 仪器化测试（迁移测试 / 契约测试）；无设备时仅编译不执行
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // Compose 调试辅助
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
