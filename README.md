# CSM

Android 项目仓库（CSM）。

## 环境

- Android Studio / Gradle（JVM 17）
- compileSdk 35，minSdk 24

## 目录结构

```
.
├── app/            # 应用模块（待添加）
├── gradle/         # Gradle wrapper 配置
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

当前仓库为初始化骨架，Android 工程代码后续提交。

## 构建

```bash
./gradlew assembleDebug
```

Windows：

```bat
gradlew.bat assembleDebug
```

## 版本控制约定

- `local.properties`、`.gradle/`、`build/`、`.idea/` 中与个人环境相关的文件不入库。
- 分支：`main` 为主分支。
