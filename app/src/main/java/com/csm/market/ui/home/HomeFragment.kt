package com.csm.market.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.csm.market.R
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.sample.SampleData
import com.csm.market.databinding.FragmentHomeBinding
import com.google.android.material.chip.Chip

/**
 * 首页：商品列表 + 分类筛选。
 *
 * 第 1 次提交使用本地样例数据，接入网络后这里只改数据来源（ViewModel），
 * 列表、空态、筛选交互保持不变。
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val adapter = ProductAdapter { product ->
        findNavController().navigate(
            R.id.action_home_to_detail,
            bundleOf("productId" to product.id)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    toast(getString(R.string.feature_coming_soon))
                    true
                }
                else -> false
            }
        }

        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = adapter

        binding.fabPublish.setOnClickListener {
            findNavController().navigate(R.id.publishFragment)
        }

        setupCategoryChips()
        renderProducts(SampleData.products)
    }

    private fun setupCategoryChips() {
        val chipGroup = binding.chipGroupCategory
        chipGroup.removeAllViews()

        val allChip = createChip(getString(R.string.category_all), selected = true)
        allChip.tag = null
        chipGroup.addView(allChip)

        ProductCategory.entries.forEach { category ->
            val chip = createChip(category.label, selected = false)
            chip.tag = category
            chipGroup.addView(chip)
        }

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: View.NO_ID)
            val category = chip?.tag as? ProductCategory
            val data = if (category == null) {
                SampleData.products
            } else {
                SampleData.products.filter { it.category == category }
            }
            renderProducts(data)
        }
    }

    private fun createChip(text: String, selected: Boolean): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            isCheckable = true
            isChecked = selected
            setEnsureMinTouchTargetSize(false)
            chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke)
        }
    }

    private fun renderProducts(products: List<com.csm.market.data.model.Product>) {
        adapter.submitList(products)
        val isEmpty = products.isEmpty()
        binding.emptyState.isVisible = isEmpty
        binding.rvProducts.isVisible = !isEmpty
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        binding.rvProducts.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
