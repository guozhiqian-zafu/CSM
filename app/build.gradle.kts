plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.csm.market"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.csm.market"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 后端地址：模拟器访问宿主机的固定别名是 10.0.2.2（MuMu、AVD 都适用）。
        // 真机联调时改成电脑的局域网 IP，例如 http://192.168.1.5:8080/
        // 本机 8080 被其他服务占用时，可以用 -PcsmApiBaseUrl=http://10.0.2.2:18080/ 临时改端口，
        // 不必改代码：默认值仍然是文档里写的 8080。
        val apiBaseUrl = (project.findProperty("csmApiBaseUrl") as String?)
            ?: "http://10.0.2.2:8080/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // 默认的 debug keystore 放在 ~/.android 下，某些受限环境（用户目录只读）无法创建，
    // 这里优先使用仓库内的 keystore/debug.keystore（已在 .gitignore 中排除）。
    // 没有该文件时退回 AGP 默认行为，保证别人 clone 下来也能直接编译。
    signingConfigs {
        val localDebugKeystore = rootProject.file("keystore/debug.keystore")
        if (localDebugKeystore.exists()) {
            getByName("debug") {
                storeFile = localDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // 伴生模块 P1 桌面小组件（独立模块，随 App 一起打包）
    implementation(project(":companion"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.kotlinx.coroutines.android)

    // Room：本地持久化。用 KSP 生成 DAO 实现，比 kapt 快、且 Kotlin 2.x 官方推荐。
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 网络：Retrofit + OkHttp + kotlinx.serialization，与服务端共用一套 JSON 字段名
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // 图片：Coil 负责把服务端返回的 URL 加载进 ImageView（自带内存/磁盘缓存）
    implementation(libs.coil)
    // 压缩前读 EXIF 方向，避免竖拍的图在列表里躺倒
    implementation(libs.androidx.exifinterface)
    // 留言提醒：后台周期任务 + 通知
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
