package com.csm.market.ui.messages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.csm.market.R
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Conversation
import com.csm.market.databinding.FragmentConversationsBinding
import com.csm.market.ui.common.showEmpty
import com.csm.market.ui.common.showError
import com.csm.market.ui.common.showLoading
import com.csm.market.ui.common.hideState
import kotlinx.coroutines.launch

/**
 * 「我的留言」：按商品聚合的会话列表。
 *
 * 点某条会话进入该商品的留言详情，复用已有的 MessageFragment（带商品 id 与标题）。
 */
class ConversationsFragment : Fragment() {

    private var _binding: FragmentConversationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConversationsViewModel by viewModels { ConversationsViewModel.Factory }

    private val adapter = ConversationAdapter { openConversation(it) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rvConversations.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::renderState)
            }
        }
    }

    private fun renderState(state: UiState<List<Conversation>>) {
        when (state) {
            is UiState.Loading -> {
                binding.rvConversations.isVisible = false
                binding.uiState.showLoading()
            }

            is UiState.Empty -> {
                binding.rvConversations.isVisible = false
                adapter.submitList(emptyList())
                binding.uiState.showEmpty(
                    R.string.conversations_empty_title,
                    R.string.conversations_empty_desc,
                    R.string.conversations_go_shopping
                ) {
                    findNavController().navigate(R.id.homeFragment)
                }
            }

            is UiState.Error -> {
                binding.rvConversations.isVisible = false
                adapter.submitList(emptyList())
                binding.uiState.showError(state.error) { viewModel.retry() }
            }

            is UiState.Success -> {
                binding.uiState.hideState()
                binding.rvConversations.isVisible = true
                adapter.submitList(state.data)
            }
        }
    }

    private fun openConversation(conversation: Conversation) {
        findNavController().navigate(
            R.id.action_conversations_to_messages,
            bundleOf(
                "productId" to conversation.productId,
                "productTitle" to conversation.productTitle
            )
        )
    }

    override fun onDestroyView() {
        binding.rvConversations.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
