package com.csm.market.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.csm.market.R
import com.csm.market.databinding.FragmentProfileBinding

/**
 * 「我的」页：个人资料入口 + 我的发布 / 我的留言 / 设置。
 * 这些入口会在伴生模块（桌面小组件）联调时复用同一套统计接口。
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val comingSoon = View.OnClickListener {
            Toast.makeText(requireContext(), R.string.feature_coming_soon, Toast.LENGTH_SHORT).show()
        }
        binding.itemMyListings.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_my_listings)
        }
        binding.itemMyMessages.setOnClickListener(comingSoon)
        binding.itemSettings.setOnClickListener(comingSoon)
        binding.btnLogin.setOnClickListener(comingSoon)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
