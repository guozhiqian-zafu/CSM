package com.csm.market.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.csm.market.data.model.Product
import com.csm.market.databinding.ItemProductBinding
import com.csm.market.util.formatPrice
import com.csm.market.util.GeoUtils
import com.csm.market.util.formatRelativeTime

/**
 * 商品列表适配器。
 *
 * 使用 ListAdapter + DiffUtil：后续接入网络分页时，只需要 submitList 新数据，
 * 不需要手动 notifyDataSetChanged，避免列表闪烁。
 */
class ProductAdapter(
    private val onItemClick: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProductViewHolder(
        private val binding: ItemProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.ivCover.load(product.coverUrl) {
                crossfade(true)
                placeholder(product.coverRes)
                error(product.coverRes)
            }
            binding.tvTitle.text = product.title
            binding.tvPrice.text = formatPrice(product.price)
            binding.tvOriginalPrice.text = product.originalPrice?.let(::formatPrice).orEmpty()
            binding.tvOriginalPrice.paint.isStrikeThruText = true
            binding.tvCondition.text = product.condition.label
            binding.tvMeta.text = buildString {
                append(product.location)
                append(" · ")
                append(formatRelativeTime(product.publishedMinutesAgo))
                distances[product.id]?.let { meters ->
                    append(" · ")
                    append(GeoUtils.formatDistance(meters))
                }
            }
            binding.root.setOnClickListener { onItemClick(product) }
        }
    }

    /**
     * 商品 id → 距离（米）。
     *
     * 距离不放进 [Product]：它是「用户此刻在哪」派生的数据，不属于商品本身；
     * 放进列表模型会让 DiffUtil 认为商品变了，附近排序一开列表整体闪一下。
     */
    var distances: Map<String, Double> = emptyMap()

    private object DiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean =
            oldItem == newItem
    }
}
