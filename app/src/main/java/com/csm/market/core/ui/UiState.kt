package com.csm.market.core.ui

import com.csm.market.core.error.CsmError

/**
 * 页面统一状态。
 *
 * 为什么用 sealed interface 而不是三个 Boolean（isLoading / isEmpty / hasError）：
 * 三个 Boolean 一共 8 种组合，其中 5 种是非法状态（比如「正在加载且已有错误」），
 * UI 层迟早会踩到。这里用密封类型把合法状态收敛成 4 个，编译器替我们守住互斥性。
 *
 * 第 2 次提交先在首页/详情页落地；第 3 次提交会由 ViewModel 通过 StateFlow 产出它。
 */
sealed interface UiState<out T> {

    /** 首次加载 / 下拉刷新中 */
    data object Loading : UiState<Nothing>

    /** 请求成功但结果为空（和「出错」是两回事，文案也不同） */
    data object Empty : UiState<Nothing>

    /** 任何失败，统一收敛成 [CsmError] */
    data class Error(val error: CsmError) : UiState<Nothing>

    /**
     * 正常数据。
     * 注意这里没有再包一层 `data class Success(val data: T, val isLoading: Boolean)`，
     * 分页加载更多这类「Success + 追加中」的场景交给列表自身的 footer 状态处理，
     * 页面级状态保持单一来源。
     */
    data class Success<T>(val data: T) : UiState<T>
}

/** 取出成功数据，其他状态返回 null——UI 里少写一次强制类型转换 */
fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Success)?.data

/** 是否处于加载态，用于「加载中不显示空态」这类判断 */
fun <T> UiState<T>.isLoading(): Boolean = this is UiState.Loading

/** 把成功数据映射成另一种类型，保持状态语义不变（Loading/Empty/Error 原样透传） */
inline fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Loading -> UiState.Loading
    is UiState.Empty -> UiState.Empty
    is UiState.Error -> this
    is UiState.Success -> UiState.Success(transform(data))
}
