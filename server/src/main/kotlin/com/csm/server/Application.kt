package com.csm.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json

/**
 * 校园集市后端入口。
 *
 * 为什么自己写后端而不是接一个公开 API：
 * 1. 课程要求「网络 + 云存储」，商品是用户发布的数据，没有现成的公开接口能用；
 * 2. 数据结构要和 App 的 `Product` 一一对应（成色系数、分类中位数定价），
 *    接第三方接口反而要写一层别扭的适配；
 * 3. 后端只有几百行，随仓库一起提交，实验课上可以现场启动、现场演示真实请求。
 *
 * 启动：`gradlew :server:run`（默认 8080 端口），数据落在 `server/data/csm.db`。
 */
fun main() {
    embeddedServer(Netty, port = port(), host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun port(): Int = System.getenv("CSM_PORT")?.toIntOrNull() ?: 8080

fun Application.module() {
    install(ContentNegotiation) {
        json(
            Json {
                encodeDefaults = true
                explicitNulls = false
                prettyPrint = false
                ignoreUnknownKeys = true
            }
        )
    }

    // App 调试时可能出现 WebView/浏览器预览，跨域放开只读接口，写接口仍受业务校验
    install(CORS) {
        anyHost()
        allowHeaders { true }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("未处理异常", cause)
            call.respond(
                envelope<Unit>(
                    code = ErrorCode.SERVER,
                    message = "服务器开小差了，请稍后重试"
                )
            )
        }
    }

    val store = ProductStore.open()
    registerProductRoutes(store)
    registerUploadRoutes()
}
