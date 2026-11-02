pluginManagement {
    repositories {
        // 国内镜像优先，下载更快；镜像缺失时自动回退官方仓库
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "CSM"
include(":app")
// 自建轻量后端（Ktor + SQLite）：与 App 同仓库，方便演示「真实接口」与「云存储」
include(":server")
