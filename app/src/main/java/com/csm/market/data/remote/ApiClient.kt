package com.csm.market.data.remote

import com.csm.market.BuildConfig
import com.csm.market.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络客户端的组装处。
 *
 * 三个配置值得在答辩时讲清楚：
 * - 超时：连接 10s、读 20s。校园网抖动时让用户看到「连不上」而不是无限转圈；
 * - 日志：只 debug 构建打开，release 不发请求体日志（可能含用户隐私）；
 * - Json：`ignoreUnknownKeys = true` 让服务端新增字段不会把老版本 App 崩掉，
 *   `explicitNulls = false` 与 Ktor 服务端的序列化配置保持一致。
 */
object ApiClient {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val api: CsmApi by lazy { create(BuildConfig.API_BASE_URL) }

    /** 抽出来是为了单测可以指向 MockWebServer / 本地假服务，不必反射改单例。 */
    fun create(baseUrl: String): CsmApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CsmApi::class.java)
}

/** 服务端返回了非 0 的业务码，或 HTTP 状态码异常。 */
class ApiException(
    val code: Int,
    val httpStatus: Int,
    override val message: String
) : RuntimeException(message)

/**
 * 统一解包：把 `Response<ApiEnvelope<T>>` 变成「T 或 null」，出错抛 [ApiException]。
 *
 * 404 + code 2001 视为「明确不存在」（返回 null），其余非 0 码一律抛异常，
 * 这样仓库层能区分「没数据」和「请求失败」——前者显示空态，后者显示错误态。
 */
fun <T> retrofit2.Response<ApiEnvelope<T>>.unwrap(): T? {
    val envelope = body()
    if (!isSuccessful) {
        val code = envelope?.code ?: -1
        val message = envelope?.message ?: "请求失败（HTTP ${code()}）"
        if (this.code() == 404 && code == 2001) return null
        throw ApiException(code = code, httpStatus = this.code(), message = message)
    }
    if (envelope == null) throw ApiException(code = -1, httpStatus = 200, message = "响应体为空")
    if (envelope.code != 0) throw ApiException(envelope.code, this.code(), envelope.message)
    return envelope.data
}
