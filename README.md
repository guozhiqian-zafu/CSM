# CSM · 校园集市

> 2024 级 Android 课程设计 · 主 App（2 学分）+ 伴生模块（1 学分）
> 学号：202405100221　姓名：郭祉谦
> 选题：**B1 校园二手交易（无支付）** + 伴生模块 **P1 桌面小组件**

## 这个仓库是什么

同一份项目的两个可评分成果，在同一个仓库里分目录/分支管理：

| 成果 | 课程 | 目录 | 说明 |
| --- | --- | --- | --- |
| 主 App | Android 移动终端应用开发技术（2 学分） | `app/` | 校园二手交易平台，追求技术深度 |
| 伴生模块 | 移动互联网应用开发实战（1 学分） | `companion/`（第 8 次提交起） | 桌面小组件，读取主 App 数据，独立可演示 |

## 已定的两个必做特色功能 + 一个自选功能

按「同题防抄袭」要求，B1 之外的个性化差异是：

1. **必做 ①：智能定价助手** —— 自研估价算法（同类目历史价格中位数 × 成色系数 × 时间折旧），
   发布时给出建议价区间与「定价合理性」评分，列表里标记明显低于建议价的「捡漏」商品。
   对应最低标准里的「至少一个自定义业务逻辑模块」。
2. **必做 ②：图片处理流水线** —— 多图拍摄/相册选择 → 压缩到长边 1280px、质量 82%
   → 读取并清洗 EXIF（抹掉 GPS 等隐私字段）→ multipart 上传，带进度回调与失败重试。
   对应 B1 的「图片处理」强制技术点。
3. **自选：附近好物** —— 定位后按 Haversine 距离排序；拒绝定位权限时自动降级为默认排序
   并给出可点击的提示，App 仍然可用。对应「权限申请与降级处理」。

基础功能全部保留：商品列表、详情、发布、搜索筛选、收藏、留言。

## 技术栈

- Kotlin 2.0.21 + AndroidX，`minSdk 24 / targetSdk 35`
- Jetpack：Navigation、ViewModel、Lifecycle、Room（第 5 次提交接入）、WorkManager（第 12 次）
- Material 3（`Theme.Material3.DayNight`，支持深色模式）
- 网络：Retrofit + OkHttp（第 6 次提交接入），后端为仓库内自建的轻量服务
- 图片：CameraX + 自研压缩/EXIF 清洗；异步统一用 Coroutines + Flow
- 构建：AGP 8.9.1 + Gradle 8.11.1，JDK 17 字节码

## 本地构建

```bash
# 1. 指定 SDK 位置（本地文件，不入库）
echo "sdk.dir=C:\\Apps\\sdk" > local.properties

# 2. 编译
./gradlew assembleDebug

# 3. 跑单元测试
./gradlew testDebugUnitTest
```

> 本机环境提示 1：`JAVA_HOME` 需指向 JDK 17+（本项目在 `D:\java\jdk`，JDK 21）后再执行 Gradle。
>
> 本机环境提示 2：如果 Windows 用户名或工程路径里含中文，Gradle 测试任务会报
> `ClassNotFoundException: GradleWorkerMain`。此时把 Gradle 用户目录改到纯英文路径即可：
> `set GRADLE_USER_HOME=D:\AndroidGradle`（Android Studio 在
> `Settings → Build Tools → Gradle → Gradle user home` 改同一项）。
> 详见 [docs/07-测试与问题记录.md](docs/07-测试与问题记录.md) 问题 7。

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/01-选题与需求分析.md](docs/01-选题与需求分析.md) | 选题背景、用户与场景、竞品与差异 |
| [docs/02-功能清单.md](docs/02-功能清单.md) | 功能清单 + 对照课程最低复杂度标准逐条说明 |
| [docs/03-系统设计与数据模型.md](docs/03-系统设计与数据模型.md) | 分层架构、页面结构、ER 模型、模块划分 |
| [docs/04-接口设计.md](docs/04-接口设计.md) | 后端 REST 接口、请求/响应示例、错误码 |
| [docs/05-开发计划与提交记录.md](docs/05-开发计划与提交记录.md) | 15 次提交的计划与实际记录、里程碑对应 |
| [docs/06-个性化功能设计.md](docs/06-个性化功能设计.md) | 智能定价算法、图片流水线、附近好物的设计与公式 |
| [docs/07-测试与问题记录.md](docs/07-测试与问题记录.md) | 测试用例、真机/模拟器验证记录、踩坑与修复 |
| [docs/08-伴生模块设计.md](docs/08-伴生模块设计.md) | P1 桌面小组件的交互、数据来源与刷新策略 |

## 提交规范

提交信息统一用 Conventional Commits：

- `feat:` 新功能　`fix:` 修复　`refactor:` 重构　`docs:` 文档
- `chore:` 构建/依赖　`test:` 测试　`style:` 格式

例如：`feat(home): 完成商品列表与分类筛选`。每个大功能拆成「UI → 逻辑 → 联调」多次提交，
详见 [docs/05-开发计划与提交记录.md](docs/05-开发计划与提交记录.md)。
