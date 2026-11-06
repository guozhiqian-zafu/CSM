package com.csm.market.ui.messages

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.csm.market.R
import com.csm.market.data.model.Conversation
import com.csm.market.databinding.ItemConversationBinding
import coil.load
import com.csm.market.util.formatRelativeTime

/**
 * 会话列表适配器。
 *
 * 与商品列表不同，这里按 `productId` 比对身份：同一个商品的会话只有一条，
 * 内容变了只刷新这一行，避免整表重建导致滚动位置跳动。
 */
class ConversationAdapter(
    private val onItemClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemConversationBinding,
        private val onItemClick: (Conversation) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Conversation) {
            binding.tvTitle.text = item.productTitle
            binding.tvLastMessage.text = item.lastContent
            binding.tvTime.text = formatRelativeTime(item.lastMinutesAgo)

            // 未读角标：只有别人发来、且还没读的才计数
            binding.tvUnread.isVisible = item.unreadCount > 0
            binding.tvUnread.text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString()

            binding.ivCover.load(item.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_photo_1)
                error(R.drawable.bg_photo_1)
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean =
            oldItem.productId == newItem.productId

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean =
            oldItem == newItem
    }
}
