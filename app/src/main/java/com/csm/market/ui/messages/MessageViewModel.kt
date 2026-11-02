package com.csm.market.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.csm.market.CsmApp
import com.csm.market.R
import com.csm.market.core.error.CsmError
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Message
import com.csm.market.data.repo.MessageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 留言页 ViewModel。
 *
 * 页面状态只有一份 [UiState]，发送中的输入框状态单独放：发送失败要把用户
 * 打的字留在输入框里，不能因为列表回到错误态就把草稿一起清掉。
 */
class MessageViewModel(
    private val repository: MessageRepository,
    private val productId: String
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Message>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Message>>> = _state.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** 一次性提示（Toast），避免旋转屏幕后重复弹出。 */
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _state.value = UiState.Loading
            try {
                val list = repository.messages(productId)
                _state.value = if (list.isEmpty()) UiState.Empty else UiState.Success(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UiState.Error(CsmError.from(e))
            }
        }
    }

    fun retry() = load()

    fun onDraftChange(text: String) {
        _draft.value = text
    }

    fun send() {
        val content = _draft.value.trim()
        if (content.isEmpty() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            try {
                val created = repository.send(productId, content)
                _draft.value = ""
                val current = (_state.value as? UiState.Success)?.data.orEmpty()
                _state.value = UiState.Success(current + created)
                _messages.tryEmit(R.string.messages_sent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 失败保留草稿，用户不用重新打一遍
                _messages.tryEmit(
                    if (e is com.csm.market.data.remote.ApiException) {
                        R.string.messages_send_rejected
                    } else {
                        R.string.messages_send_failed
                    }
                )
            } finally {
                _sending.value = false
            }
        }
    }

    companion object {
        fun factory(productId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CsmApp
                MessageViewModel(app.container.messageRepository, productId)
            }
        }
    }
}
