package com.csm.market.core.error

import androidx.annotation.StringRes
import com.csm.market.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 统一错误模型。
 *
 * 设计取舍：
 * - UI 只认这个 sealed class，不接触 IOException / HttpException / 任何网络库的异常类型，
 *   以后从 Retrofit 换到别的实现，UI 一行都不用改。
 * - 文案（title/message）放在资源里而不是异常信息里，才能做本地化和深色模式一致的排版。
 * - [retryable] 决定错误态是否出现「重试」按钮：像「商品不存在」这种重试一万次也没用。
 */
sealed class CsmError(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val retryable: Boolean = true,
) {

    /** 无网络 / DNS 解析失败 */
    data object Network : CsmError(R.string.error_network_title, R.string.error_network_desc)

    /** 连接或读取超时（校园网高峰期很常见） */
    data object Timeout : CsmError(R.string.error_timeout_title, R.string.error_timeout_desc)

    /** 资源不存在：商品被下架、链接失效 */
    data object NotFound :
        CsmError(R.string.error_not_found_title, R.string.error_not_found_desc, retryable = false)

    /** 权限被拒绝：定位、相机、通知 */
    data object PermissionDenied :
        CsmError(R.string.error_permission_title, R.string.error_permission_desc, retryable = false)

    /** 兜底。code 保留服务端业务码，便于日志排查 */
    data class Unknown(val code: Int = -1) :
        CsmError(R.string.error_unknown_title, R.string.error_unknown_desc)

    companion object {

        /** 把底层异常翻译成用户能看懂的错误，避免把 stacktrace 直接甩给用户 */
        fun from(throwable: Throwable): CsmError = when (throwable) {
            is SocketTimeoutException -> Timeout
            is UnknownHostException -> Network
            is IOException -> Network
            else -> Unknown()
        }
    }
}
