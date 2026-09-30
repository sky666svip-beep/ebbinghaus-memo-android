// :core —— 纯 Kotlin JVM 领域模块（零 Android 依赖）
//
// 承载艾宾浩斯调度算法、复习档位模型与数学文本预处理器等纯领域逻辑。
// 包名保持 com.ebbinghaus.memo.core.* 不变，避免全项目 import churn。
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 纯 JVM 单元测试
    testImplementation("junit:junit:4.13.2")
}
