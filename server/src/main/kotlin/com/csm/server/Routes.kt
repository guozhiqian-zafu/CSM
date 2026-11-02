package com.csm.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.MissingFieldException

/**
 * 商品相关路由。
 *
 * 约定（与 docs/04 一致）：
 * - HTTP 状态码保持语义（200/400/404/500），业务分支看响应体里的 `code`；
 * - 参数错误一律返回 `code=1001` 且 message 指出具体字段，方便客户端直接展示。
 */
fun Application.registerProductRoutes(store: ProductStore) {
    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "csm-market"))
        }

        route("/api/products") {
            // GET /api/products?page=1&size=20&category=BOOK&keyword=书&sort=latest
            get {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                val category = call.request.queryParameters["category"]
                val keyword = call.request.queryParameters["keyword"]
                val condition = call.request.queryParameters["condition"]
                val sort = call.request.queryParameters["sort"]
                val minPrice = call.request.queryParameters["minPrice"]?.toDoubleOrNull()
                val maxPrice = call.request.queryParameters["maxPrice"]?.toDoubleOrNull()
                // 我的发布页靠它只看自己发布的东西
                val sellerId = call.request.queryParameters["sellerId"]

                if (category != null && category !in validCategories) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        envelope<Unit>(ErrorCode.PARAM, "category 取值非法：$category")
                    )
                    return@get
                }
                if (minPrice != null && minPrice < 0 || maxPrice != null && maxPrice < 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        envelope<Unit>(ErrorCode.PARAM, "价格区间不能为负数")
                    )
                    return@get
                }
                if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        envelope<Unit>(ErrorCode.PARAM, "minPrice 不能大于 maxPrice")
                    )
                    return@get
                }

                val result = store.list(
                    page = page,
                    size = size,
                    category = category,
                    keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
                    minPrice = minPrice,
                    maxPrice = maxPrice,
                    condition = condition,
                    sort = sort,
                    sellerId = sellerId
                )
                call.respond(envelope(data = result))
            }

            // GET /api/products/{id}
            get("/{id}") {
                val id = call.parameters["id"].orEmpty()
                val product = store.find(id)
                if (product == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        envelope<Unit>(ErrorCode.NOT_FOUND, "商品不存在或已下架")
                    )
                } else {
                    call.respond(envelope(data = product))
                }
            }

            // POST /api/products（第 5 次提交先收 JSON；第 9 次提交的图片上传再改 multipart）
            post {
                // 解析失败要分清「不是 JSON」和「少了必填字段」，否则前端只能干瞪眼。
                // Ktor 的转换器会把 kotlinx 的异常再包一层，所以要顺着 cause 链找。
                val request = try {
                    call.receive<CreateProductRequest>()
                } catch (e: Exception) {
                    val missing = e.findMissingFields()
                    val message = if (missing != null) "缺少必填字段：$missing" else "请求体不是合法的商品 JSON"
                    call.respond(
                        HttpStatusCode.BadRequest,
                        envelope<Unit>(ErrorCode.PARAM, message)
                    )
                    return@post
                }

                val invalidMessage = validate(request)
                if (invalidMessage != null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        envelope<Unit>(ErrorCode.PARAM, invalidMessage)
                    )
                    return@post
                }

                val created = store.create(request)
                call.respond(HttpStatusCode.Created, envelope(data = created))
            }

            // GET /api/products/{id}/messages：按时间正序，聊天式排列
            get("/{id}/messages") {
                val id = call.parameters["id"].orEmpty()
                if (store.find(id) == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        envelope<Unit>(ErrorCode.NOT_FOUND, "商品不存在或已下架")
                    )
                    return@get
                }
                call.respond(envelope(data = store.listMessages(id)))
            }

            // POST /api/products/{id}/messages
            post("/{id}/messages") {
                val id = call.parameters["id"].orEmpty()
                val request = try {
                    call.receive<CreateMessageRequest>()
                } catch (e: Exception) {
                    val missing = e.findMissingFields()
                    val message = if (missing != null) "缺少必填字段：$missing" else "请求体不是合法的留言 JSON"
                    call.respond(
                        HttpStatusCode.BadRequest,
                        envelope<Unit>(ErrorCode.PARAM, message)
                    )
                    return@post
                }
                val content = request.content.trim()
                val invalid = when {
                    content.isEmpty() -> "留言内容不能为空"
                    content.length > 300 -> "留言不能超过 300 个字"
                    request.senderName.length > 20 -> "昵称不能超过 20 个字"
                    else -> null
                }
                if (invalid != null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        envelope<Unit>(ErrorCode.PARAM, invalid)
                    )
                    return@post
                }
                val created = store.addMessage(id, request.copy(content = content))
                if (created == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        envelope<Unit>(ErrorCode.NOT_FOUND, "商品不存在或已下架")
                    )
                } else {
                    call.respond(HttpStatusCode.Created, envelope(data = created))
                }
            }

            // DELETE /api/products/{id}：软删除（下架）
            delete("/{id}") {
                val id = call.parameters["id"].orEmpty()
                if (store.takeDown(id)) {
                    call.respond(envelope(data = mapOf("id" to id, "status" to "TAKEN_DOWN")))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        envelope<Unit>(ErrorCode.NOT_FOUND, "商品不存在或已下架")
                    )
                }
            }
        }

        // GET /api/messages/unread-count：未读数，桌面小组件与「我的」角标都用它
        get("/api/messages/unread-count") {
            call.respond(envelope(data = mapOf("count" to store.unreadCount())))
        }

        // POST /api/messages/{id}/read：标记单条已读
        post("/api/messages/{id}/read") {
            val id = call.parameters["id"].orEmpty()
            if (store.markRead(id)) {
                call.respond(envelope(data = mapOf("id" to id, "read" to true)))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    envelope<Unit>(ErrorCode.NOT_FOUND, "留言不存在")
                )
            }
        }

        // GET /api/price-stats?category=BOOK
        get("/api/price-stats") {
            val category = call.request.queryParameters["category"]
            if (category != null && category !in validCategories) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    envelope<Unit>(ErrorCode.PARAM, "category 取值非法：$category")
                )
                return@get
            }
            call.respond(envelope(data = store.priceStats(category)))
        }
    }
}

/** 顺着 cause 链找 kotlinx 的必填字段异常，找到就返回字段名（用于给出更准的错误提示）。 */
private fun Throwable.findMissingFields(): String? {
    var current: Throwable? = this
    while (current != null) {
        if (current is MissingFieldException) {
            return current.missingFields.joinToString("、").ifEmpty { "未知字段" }
        }
        current = current.cause
    }
    return null
}

/** 服务端校验：客户端的表单校验不能当作安全边界，两边都要有。 */
internal fun validate(request: CreateProductRequest): String? {
    val title = request.title.trim()
    return when {
        title.isEmpty() -> "标题不能为空"
        title.length > 40 -> "标题不能超过 40 个字"
        request.price <= 0 -> "价格必须大于 0"
        request.price > 100_000 -> "价格看起来不太合理，请检查"
        request.category !in validCategories -> "分类取值非法：${request.category}"
        request.condition !in conditionFactors -> "成色取值非法：${request.condition}"
        request.description.length > 500 -> "描述不能超过 500 个字"
        request.location.length > 40 -> "交易地点不能超过 40 个字"
        else -> null
    }
}
