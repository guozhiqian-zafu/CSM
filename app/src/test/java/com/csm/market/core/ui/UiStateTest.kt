package com.csm.market.core.ui

import com.csm.market.core.error.CsmError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三态模型的纯 JVM 单测。
 *
 * 这些断言看着简单，但它们守住的是「状态互斥」这个约定：
 * 以后谁给 UiState 加了新的子类、或者改 map 的透传逻辑，这里会第一时间红。
 */
class UiStateTest {

    @Test
    fun `success 能取出数据`() {
        val state: UiState<List<Int>> = UiState.Success(listOf(1, 2, 3))

        assertEquals(listOf(1, 2, 3), state.dataOrNull())
        assertFalse(state.isLoading())
    }

    @Test
    fun `非 success 状态取数据为 null`() {
        assertNull(UiState.Loading.dataOrNull())
        assertNull(UiState.Empty.dataOrNull())
        assertNull(UiState.Error(CsmError.Timeout).dataOrNull())
    }

    @Test
    fun `loading 能被识别`() {
        assertTrue(UiState.Loading.isLoading())
        assertFalse(UiState.Success(Unit).isLoading())
    }

    @Test
    fun `map 只转换成功数据`() {
        val success: UiState<Int> = UiState.Success(2)
        assertEquals(UiState.Success(4), success.map { it * 2 })
    }

    @Test
    fun `map 不改变空态与错误态`() {
        assertEquals(UiState.Empty, UiState.Empty.map { it })

        val error = UiState.Error(CsmError.Network)
        assertEquals(error, error.map { it })
    }

    @Test
    fun `空的成功结果与空态在语义上不同`() {
        // 列表为空时由调用方决定是否降级为 Empty，模型本身不替业务做主
        val emptySuccess: UiState<List<String>> = UiState.Success(emptyList())
        assertEquals(emptyList<String>(), emptySuccess.dataOrNull())
        assertTrue(emptySuccess is UiState.Success)
    }
}
