package com.arduinoworld.smarthome

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.arduinoworld.smarthome.databinding.FragmentHeatingModeBinding
import com.google.android.material.tabs.TabLayoutMediator

class HeatingModeFragment : Fragment() {
    private lateinit var binding: FragmentHeatingModeBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fragmentList = listOf(HeatingTimerFragment(), HeatingTimeFragment(), HeatingTemperatureFragment())
        val adapter = ViewPagerAdapter(requireActivity(), fragmentList)
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = getString(R.string.tab_item_timer_mode)
                }
                1 -> {
                    tab.text = getString(R.string.tab_item_time_mode)
                }
                2 -> {
                    tab.text = getString(R.string.tab_item_temperature_mode)
                }
            }
        }.attach()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHeatingModeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }
}