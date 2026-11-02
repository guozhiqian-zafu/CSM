package com.csm.market.ui.messages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.csm.market.R
import com.csm.market.core.ui.UiState
import com.csm.market.databinding.FragmentMessagesBinding
import com.csm.market.ui.common.hideState
import com.csm.market.ui.common.showEmpty
import com.csm.market.ui.common.showError
import com.csm.market.ui.common.showLoading
import android.widget.Toast
import kotlinx.coroutines.launch

/** 留言页：列表 + 输入框，发送失败保留草稿。 */
class MessageFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private val productId: String by lazy {
        requireArguments().getString("productId").orEmpty()
    }

    private val viewModel: MessageViewModel by viewModels {
        MessageViewModel.factory(productId)
    }

    private val adapter = MessageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rvMessages.adapter = adapter

        requireArguments().getString("productTitle")?.takeIf { it.isNotBlank() }?.let { title ->
            binding.tvProductTitle.text = getString(R.string.messages_about, title)
        }

        binding.etMessage.doAfterTextChanged { viewModel.onDraftChange(it?.toString().orEmpty()) }
        binding.btnSend.setOnClickListener { viewModel.send() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::renderState) }
                launch {
                    viewModel.sending.collect { sending ->
                        binding.pbSending.isVisible = sending
                        binding.btnSend.isEnabled = !sending
                    }
                }
                launch {
                    viewModel.draft.collect { draft ->
                        // 只在内容不一致时回写，否则每敲一个字都会重置光标
                        if (binding.etMessage.text.toString() != draft) {
                            binding.etMessage.setText(draft)
                            binding.etMessage.setSelection(binding.etMessage.text.length)
                        }
                    }
                }
                launch {
                    viewModel.messages.collect { resId ->
                        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun renderState(state: UiState<List<com.csm.market.data.model.Message>>) {
        val list = (state as? UiState.Success)?.data.orEmpty()
        binding.rvMessages.isVisible = list.isNotEmpty()
        adapter.submitList(list) {
            // 新消息到达后滚到底部，符合聊天工具的习惯
            if (list.isNotEmpty()) binding.rvMessages.scrollToPosition(list.lastIndex)
        }
        when (state) {
            is UiState.Loading -> binding.uiState.showLoading()
            is UiState.Empty -> binding.uiState.showEmpty(
                R.string.messages_empty_title,
                R.string.messages_empty_desc
            )
            is UiState.Error -> binding.uiState.showError(state.error) { viewModel.retry() }
            is UiState.Success -> binding.uiState.hideState()
        }
    }

    override fun onDestroyView() {
        binding.rvMessages.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
