package com.arduinoworld.smarthome

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.arduinoworld.smarthome.databinding.FragmentBoilerModeBinding
import com.google.android.material.tabs.TabLayoutMediator

class BoilerModeFragment : Fragment() {
    private lateinit var binding: FragmentBoilerModeBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fragmentList = listOf(BoilerTimerFragment(), BoilerTimeFragment())
        val adapter = ViewPagerAdapter(requireActivity(), fragmentList)
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            if (position == 0) {
                tab.text = getString(R.string.tab_item_timer_mode)
            } else {
                tab.text = getString(R.string.tab_item_time_mode)
            }
        }.attach()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBoilerModeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }
}