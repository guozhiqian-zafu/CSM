plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * 伴生模块 P1：桌面小组件。
 *
 * 为什么做成独立 Gradle 模块而不是塞进 app：
 * - 1 学分课程要求「独立可运行的单元」，独立模块有自己的清单、资源、代码边界，
 *   Git 里也能一眼看出伴生模块的提交；
 * - 它不依赖 app 的任何类，只通过 `content://com.csm.market.widget/summary`
 *   这一个公开契约读主 App 数据——契约变了会立刻暴露，而不是悄悄耦合。
 *
 * 注意：Android 的 AppWidget 必须和主应用同包安装，所以它编译成 library 模块、
 * 由 app 依赖进来一起打包；「独立」体现在代码与数据契约上，而不是独立 APK。
 */
android {
    namespace = "com.csm.market.companion"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
}
