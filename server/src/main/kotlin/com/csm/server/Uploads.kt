package com.csm.server

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.File
import java.util.UUID

/**
 * 图片上传。
 *
 * 「云存储」在课程项目里的落地方式：文件落在服务端 `data/uploads/`，数据库只存相对路径。
 * 客户端拿到 `/uploads/xxx.jpg` 后拼上 baseUrl 就能显示。
 *
 * 这里刻意不做的事：不生成缩略图、不做内容审核、不分片续传——课程范围之外，
 * 但大小/类型校验必须做，否则一个 4GB 的请求就能把进程打挂。
 */
private const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
private val allowedExtensions = setOf("jpg", "jpeg", "png", "webp")

/** 上传目录：默认 `data/uploads`，可用 -Dcsm.uploads=... 覆盖（测试时指向临时目录）。 */
val uploadsDir: File get() = File(System.getProperty("csm.uploads", "data/uploads"))

fun Application.registerUploadRoutes() {
    routing {
        post("/api/uploads") {
            val multipart = call.receiveMultipart()
            var savedName: String? = null
            var savedBytes = 0
            var error: Pair<Int, String>? = null

            multipart.forEachPart { part ->
                try {
                    if (part is PartData.FileItem && savedName == null && error == null) {
                        val extension = part.originalFileName
                            ?.substringAfterLast('.', "")
                            ?.lowercase()
                            ?.takeIf { it in allowedExtensions }
                        if (extension == null) {
                            error = ErrorCode.PARAM to "只支持 jpg / png / webp 图片"
                            return@forEachPart
                        }
                        val bytes = part.streamProvider().readBytes()
                        if (bytes.isEmpty()) {
                            error = ErrorCode.PARAM to "上传内容为空"
                        } else if (bytes.size > MAX_UPLOAD_BYTES) {
                            error = ErrorCode.PARAM to "图片不能超过 5MB"
                        } else {
                            val name = "${UUID.randomUUID()}.$extension"
                            uploadsDir.mkdirs()
                            File(uploadsDir, name).writeBytes(bytes)
                            savedName = name
                            savedBytes = bytes.size
                        }
                    }
                } finally {
                    part.dispose()
                }
            }

            val failure = error
            val name = savedName
            when {
                failure != null -> call.respond(
                    HttpStatusCode.BadRequest,
                    envelope<Unit>(failure.first, failure.second)
                )

                name == null -> call.respond(
                    HttpStatusCode.BadRequest,
                    envelope<Unit>(ErrorCode.PARAM, "请求里没有找到图片字段 file")
                )

                else -> call.respond(
                    UploadDto(url = "/uploads/$name", bytes = savedBytes)
                )
            }
        }

        // 静态文件：只允许 uploads 目录下的「纯文件名」，挡掉 ../ 穿越
        get("/uploads/{name}") {
            val name = call.parameters["name"].orEmpty()
            val file = File(uploadsDir, name)
            if (name.contains('/') || name.contains('\\') || !file.isFile) {
                call.respond(HttpStatusCode.NotFound, envelope<Unit>(ErrorCode.NOT_FOUND, "图片不存在"))
            } else {
                call.respondFile(file)
            }
        }
    }
}
