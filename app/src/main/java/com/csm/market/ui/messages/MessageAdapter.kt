package com.csm.market.ui.messages

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.csm.market.data.model.Message
import com.csm.market.databinding.ItemMessageMineBinding
import com.csm.market.databinding.ItemMessageOtherBinding
import com.csm.market.util.formatRelativeTime

/**
 * 留言列表：两种条目（我发的 / 别人发的）。
 *
 * 用两种 ViewType 而不是一个布局里改 gravity：气泡背景、头像位置、是否显示昵称
 * 三处都不同，硬塞进一个布局会到处是 if。
 */
class MessageAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isMine) TYPE_MINE else TYPE_OTHER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_MINE) {
            MineHolder(ItemMessageMineBinding.inflate(inflater, parent, false))
        } else {
            OtherHolder(ItemMessageOtherBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is MineHolder -> holder.bind(message)
            is OtherHolder -> holder.bind(message)
        }
    }

    private class MineHolder(private val binding: ItemMessageMineBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.tvContent.text = message.content
            binding.tvTime.text = formatRelativeTime(message.publishedMinutesAgo)
        }
    }

    private class OtherHolder(private val binding: ItemMessageOtherBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.tvContent.text = message.content
            binding.tvSender.text = message.senderName
            binding.tvAvatar.text = message.senderName.take(1)
            binding.tvTime.text = formatRelativeTime(message.publishedMinutesAgo)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message) = oldItem == newItem
    }

    private companion object {
        const val TYPE_MINE = 0
        const val TYPE_OTHER = 1
    }
}
