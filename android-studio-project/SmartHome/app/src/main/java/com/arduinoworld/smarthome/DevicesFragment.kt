package com.arduinoworld.smarthome

import android.animation.Animator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.*
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.GridLayoutManager
import com.arduinoworld.smarthome.ACRemoteFragment.Companion.isTimeModeSelected
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.isHapticFeedbackEnabled
import com.arduinoworld.smarthome.MainActivity.Companion.isNetworkConnected
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.vibrator
import com.arduinoworld.smarthome.SmartDoorbellFragment.Companion.selectPhotoMode
import com.arduinoworld.smarthome.SmartRemotesFragment.Companion.selectedRemoteId
import com.arduinoworld.smarthome.databinding.FragmentDevicesBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DevicesFragment : Fragment() {
    private lateinit var binding: FragmentDevicesBinding
    private lateinit var deviceFragmentManager: FragmentManager
    private lateinit var addedDevicesRecyclerAdapter: DevicesRecyclerAdapter
    private lateinit var allDevicesList: List<Device>
    private lateinit var gson: Gson
    private var selectedDeviceId = 0
    private var adapterPosition = 0
    private var addedDevicesArrayList = ArrayList<Device>()
    private var isDeviceSelected = false
    private var heatingOrBoilerFragment = false
    private var startDelay = 0L

    private val heatingAndBoilerNodesList = listOf("heatingStarted", "heatingElements", "boilerStarted",
        "heatingTimerTime", "boilerTimerTime", "heatingOnOffTime", "timeHeatingElements", "boilerOnOffTime",
        "temperatureMode", "temperature", "settings")
    private val smartRemotesNodesList = listOf("lightRemoteButton", "tvRemoteButton",
        "acRemote", "acOnOffTime", "rgbRemoteButton", "settings")
    private val heaterNodesList = listOf("heaterStarted", "temperatureMode", "temperature", "heaterOnOffTime", "settings")

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            gson = Gson()
            allDevicesList = listOf(
                Device(R.drawable.ic_wifi_thermometer, getString(R.string.text_wifi_thermometer)),
                Device(R.drawable.ic_smart_heating, getString(R.string.text_heating_and_boiler)),
                Device(R.drawable.ic_smart_remote, getString(R.string.text_smart_ir_remote)),
                Device(R.drawable.ic_smart_doorbell, getString(R.string.text_smart_doorbell)),
                Device(R.drawable.ic_meter_reader, getString(R.string.text_meter_reader)),
                Device(R.drawable.ic_smart_heater, getString(R.string.text_smart_heater))
            )

            deviceFragmentManager = requireActivity().supportFragmentManager
            var radioButtonCheckedProgrammatically = false
            var deviceSettingsMode = false
            var remoteSettingsMode = false

            val allDevicesRecyclerAdapter = DevicesRecyclerAdapter(allDevicesList)
            allDevicesRecyclerAdapter.setOnItemClickListener(allDevicesRecyclerAdapterClickListener)
            recyclerViewAllDevices.apply {
                adapter = allDevicesRecyclerAdapter
                layoutManager = GridLayoutManager(requireActivity(), 2)
                addItemDecoration(DeviceItemDecoration())
            }
            if (sharedPreferences.getString("DevicesArrayList", "") == "") {
                buttonAddDevice.translationX = 1100f
                layoutLogo.animate().alpha(1f).setDuration(500).setStartDelay(startDelay).start()
                buttonAddDevice.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(startDelay).start()
            } else {
                addedDevicesArrayList = gson.fromJson(
                    sharedPreferences.getString("DevicesArrayList", ""),
                    object : TypeToken<ArrayList<Device?>?>() {}.type
                )
                addedDevicesRecyclerAdapter = DevicesRecyclerAdapter(addedDevicesArrayList)
                addedDevicesRecyclerAdapter.setOnItemClickListener(addedDevicesRecyclerAdapterClickListener)
                recyclerViewAddedDevices.apply {
                    adapter = addedDevicesRecyclerAdapter
                    layoutManager = GridLayoutManager(requireActivity(), 2)
                    addItemDecoration(DeviceItemDecoration())
                }

                layoutLogo.visibility = View.GONE
                buttonAddDevice.visibility = View.GONE

                recyclerViewAddedDevices.visibility = View.VISIBLE
                fabAddDevice.visibility = View.VISIBLE

                recyclerViewAddedDevices.animate().alpha(1f).setDuration(500).setStartDelay(startDelay).start()
                fabAddDevice.animate().alpha(1f).setDuration(500).setStartDelay(startDelay).start()
            }

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val childrenNodeNames = ArrayList<String>()
                    snapshot.children.forEach {
                        childrenNodeNames.add(it.key!!)
                    }

                    val addedDeviceNamesArrayList = ArrayList<String>()
                    addedDevicesArrayList.forEach {
                        when (it) {
                            Device(R.drawable.ic_wifi_thermometer, getString(R.string.text_wifi_thermometer)) -> {
                                addedDeviceNamesArrayList.add("WiFiThermometer")
                            }
                            Device(R.drawable.ic_smart_heating, getString(R.string.text_heating_and_boiler)) -> {
                                addedDeviceNamesArrayList.add("HeatingAndBoiler")
                            }
                            Device(R.drawable.ic_smart_remote, getString(R.string.text_smart_ir_remote)) -> {
                                addedDeviceNamesArrayList.add("SmartRemotes")
                            }
                            Device(R.drawable.ic_smart_doorbell, getString(R.string.text_smart_doorbell)) -> {
                                addedDeviceNamesArrayList.add("SmartDoorbell")
                            }
                            Device(R.drawable.ic_meter_reader, getString(R.string.text_meter_reader)) -> {
                                addedDeviceNamesArrayList.add("MeterReader")
                            }
                            Device(R.drawable.ic_smart_heater, getString(R.string.text_smart_heater)) -> {
                                addedDeviceNamesArrayList.add("Heater")
                            }
                        }
                    }

                    val arrayListSize = addedDevicesArrayList.size
                    if (!(childrenNodeNames.size == arrayListSize && childrenNodeNames.containsAll(addedDeviceNamesArrayList))) {
                        addedDevicesArrayList.clear()
                        childrenNodeNames.forEach {
                            when (it) {
                                "WiFiThermometer" -> {
                                    addedDevicesArrayList.add(Device(R.drawable.ic_wifi_thermometer, getString(R.string.text_wifi_thermometer)))
                                }
                                "HeatingAndBoiler" -> {
                                    addedDevicesArrayList.add(Device(R.drawable.ic_smart_heating, getString(R.string.text_heating_and_boiler)))
                                }
                                "SmartRemotes" -> {
                                    addedDevicesArrayList.add(Device(R.drawable.ic_smart_remote, getString(R.string.text_smart_ir_remote)))
                                }
                                "SmartDoorbell" -> {
                                    addedDevicesArrayList.add(Device(R.drawable.ic_smart_doorbell, getString(R.string.text_smart_doorbell)))
                                }
                                "MeterReader" -> {
                                    addedDevicesArrayList.add(Device(R.drawable.ic_meter_reader, getString(R.string.text_meter_reader)))
                                }
                                "Heater" -> {
                                    addedDevicesArrayList.add(Device(R.drawable.ic_smart_heater, getString(R.string.text_smart_heater)))
                                }
                            }
                        }

                        if (addedDevicesArrayList.size == 0) {
                            if (arrayListSize >= 1) {
                                editPreferences.putString("DevicesArrayList", "").apply()

                                var isAnimationStarted = true
                                recyclerViewAddedDevices.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                                fabAddDevice.animate().alpha(0f).setDuration(500).setStartDelay(0)
                                    .setListener(object: Animator.AnimatorListener {
                                        override fun onAnimationStart(animation: Animator) {}

                                        override fun onAnimationEnd(animation: Animator) {
                                            if (isAnimationStarted) {
                                                isAnimationStarted = false

                                                recyclerViewAddedDevices.visibility = View.GONE
                                                fabAddDevice.visibility = View.GONE

                                                layoutLogo.visibility = View.VISIBLE
                                                buttonAddDevice.visibility = View.VISIBLE

                                                buttonAddDevice.translationX = 1100f
                                                layoutLogo.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                                buttonAddDevice.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                            }
                                        }

                                        override fun onAnimationCancel(animation: Animator) {}
                                        override fun onAnimationRepeat(animation: Animator) {}

                                    }).start()
                            }
                        } else {
                            editPreferences.putString("DevicesArrayList", gson.toJson(addedDevicesArrayList)).apply()
                            if (arrayListSize >= 1) {
                                addedDevicesRecyclerAdapter.notifyDataSetChanged()
                            } else {
                                var isAnimationStarted = true
                                layoutLogo.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                                buttonAddDevice.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0)
                                    .setListener(object : Animator.AnimatorListener {
                                        override fun onAnimationStart(animation: Animator) {}

                                        override fun onAnimationEnd(animation: Animator) {
                                            if (isAnimationStarted) {
                                                isAnimationStarted = false

                                                layoutLogo.visibility = View.GONE
                                                buttonAddDevice.visibility = View.GONE

                                                recyclerViewAddedDevices.visibility = View.VISIBLE
                                                fabAddDevice.visibility = View.VISIBLE

                                                recyclerViewAddedDevices.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                                fabAddDevice.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                            }
                                        }

                                        override fun onAnimationCancel(animation: Animator) {}
                                        override fun onAnimationRepeat(animation: Animator) {}

                                    }).start()

                                addedDevicesRecyclerAdapter = DevicesRecyclerAdapter(addedDevicesArrayList)
                                addedDevicesRecyclerAdapter.setOnItemClickListener(addedDevicesRecyclerAdapterClickListener)
                                recyclerViewAddedDevices.apply {
                                    adapter = addedDevicesRecyclerAdapter
                                    layoutManager = GridLayoutManager(requireActivity(), 2)
                                    addItemDecoration(DeviceItemDecoration())
                                }
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}

            })

            buttonAddDevice.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    var isAnimationStarted = true
                    layoutLogo.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                    buttonAddDevice.animate().alpha(0f).translationX(-1100f).setDuration(500).setStartDelay(0)
                        .setListener(object : Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    layoutLogo.visibility = View.GONE
                                    buttonAddDevice.visibility = View.GONE
                                    
                                    recyclerViewAllDevices.visibility = View.VISIBLE
                                    fabBack.visibility = View.VISIBLE

                                    recyclerViewAllDevices.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    fabBack.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            fabAddDevice.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    var isAnimationStarted = true
                    recyclerViewAddedDevices.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                    fabAddDevice.animate().alpha(0f).setDuration(500).setStartDelay(0)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    recyclerViewAddedDevices.visibility = View.GONE
                                    fabAddDevice.visibility = View.GONE

                                    recyclerViewAllDevices.visibility = View.VISIBLE
                                    fabBack.visibility = View.VISIBLE

                                    recyclerViewAllDevices.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    fabBack.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            fabBack.setOnClickListener {
                vibrate()
                var isAnimationStarted = true
                if (addedDevicesArrayList.size > 0) {
                    if (!isDeviceSelected) {
                        recyclerViewAllDevices.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                        fabBack.animate().alpha(0f).setDuration(500).setStartDelay(0)
                            .setListener(object : Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}

                                override fun onAnimationEnd(animation: Animator) {
                                    if (isAnimationStarted) {
                                        isAnimationStarted = false

                                        recyclerViewAllDevices.visibility = View.GONE
                                        fabBack.visibility = View.GONE

                                        recyclerViewAddedDevices.visibility = View.VISIBLE
                                        fabAddDevice.visibility = View.VISIBLE

                                        recyclerViewAddedDevices.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                        fabAddDevice.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    }
                                }

                                override fun onAnimationCancel(animation: Animator) {}
                                override fun onAnimationRepeat(animation: Animator) {}

                            }).start()
                    } else {
                        if (!deviceSettingsMode) {
                            if (!((selectedDeviceId == 2 && selectedRemoteId != 0) || (selectedDeviceId == 3 && selectPhotoMode != 0))) {
                                isDeviceSelected = false
                                if (selectedDeviceId != 5) {
                                    frameLayoutDevices.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                                } else {
                                    tabLayoutHeater.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                                    viewPagerHeater.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                                }
                                if (selectedDeviceId == 1) radioGroupHeatingOrBoiler.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                                fabBack.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                                fabDeviceSettings.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                                fabDeleteDevice.animate().alpha(0f).setDuration(500).setStartDelay(0)
                                    .setListener(object: Animator.AnimatorListener {
                                        override fun onAnimationStart(animation: Animator) {}

                                        override fun onAnimationEnd(animation: Animator) {
                                            if (isAnimationStarted) {
                                                isAnimationStarted = false

                                                frameLayoutDevices.visibility = View.GONE
                                                tabLayoutHeater.visibility = View.GONE
                                                viewPagerHeater.visibility = View.GONE
                                                radioGroupHeatingOrBoiler.visibility = View.GONE
                                                fabBack.visibility = View.GONE
                                                fabDeviceSettings.visibility = View.GONE
                                                fabDeleteDevice.visibility = View.GONE

                                                Handler(Looper.getMainLooper()).postDelayed({
                                                    removeFragment()
                                                }, 1000)

                                                radioButtonCheckedProgrammatically = true
                                                buttonHeating.isChecked = true
                                                buttonBoiler.isChecked = false
                                                buttonHeating.setTextColor(Color.parseColor("#FFFFFFFF"))
                                                buttonBoiler.setTextColor(Color.parseColor("#6A61AD"))
                                                radioButtonCheckedProgrammatically = false

                                                recyclerViewAddedDevices.visibility = View.VISIBLE
                                                fabAddDevice.visibility = View.VISIBLE

                                                recyclerViewAddedDevices.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                                fabAddDevice.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                            }
                                        }

                                        override fun onAnimationCancel(animation: Animator) {}
                                        override fun onAnimationRepeat(animation: Animator) {}

                                    }).start()
                            } else {
                                if (selectedDeviceId == 2) {
                                    if (!remoteSettingsMode) {
                                        if (!isTimeModeSelected) {
                                            selectedRemoteId = 0
                                            editPreferences.putBoolean("CloseRemote", true).apply()
                                        } else {
                                            isTimeModeSelected = false
                                            editPreferences.putBoolean("CloseTimeMode", true).apply()
                                        }
                                    } else {
                                        remoteSettingsMode = false
                                        editPreferences.putBoolean("CloseRemoteSettingsMode", true).apply()
                                        fabDeviceSettings.visibility = View.VISIBLE
                                        fabDeleteDevice.visibility = View.VISIBLE
                                        fabDeviceSettings.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                        fabDeleteDevice.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    }
                                } else {
                                    selectPhotoMode = 0
                                    editPreferences.putBoolean("CancelPhotoMode", true).apply()
                                }
                            }
                        } else {
                            deviceSettingsMode = false
                            var isDeviceSettingsAnimationStarted = true
                            frameLayoutDevices.animate().alpha(0f).setDuration(500).setStartDelay(0)
                                .setListener(object: Animator.AnimatorListener {
                                    override fun onAnimationStart(animation: Animator) {}

                                    override fun onAnimationEnd(animation: Animator) {
                                        if (isDeviceSettingsAnimationStarted) {
                                            isDeviceSettingsAnimationStarted = false

                                            when(selectedDeviceId) {
                                                0 -> {
                                                    deviceFragmentManager.beginTransaction().replace(R.id.frameLayoutDevices, WiFiThermometerFragment()).commit()
                                                }
                                                1 -> {
                                                    radioGroupHeatingOrBoiler.visibility = View.VISIBLE
                                                    deviceFragmentManager.beginTransaction().replace(R.id.frameLayoutDevices, HeatingModeFragment()).commit()
                                                    radioGroupHeatingOrBoiler.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                                }
                                                2 -> {
                                                    deviceFragmentManager.beginTransaction().replace(R.id.frameLayoutDevices, SmartRemotesFragment()).commit()
                                                }
                                                3 -> {
                                                    deviceFragmentManager.beginTransaction().replace(R.id.frameLayoutDevices, SmartDoorbellFragment()).commit()
                                                }
                                                4 -> {
                                                    deviceFragmentManager.beginTransaction().replace(R.id.frameLayoutDevices, MeterReaderFragment()).commit()
                                                }
                                                5 -> {
                                                    val fragmentTransaction = deviceFragmentManager.beginTransaction()
                                                    fragmentTransaction.remove(DeviceSettingsFragment())
                                                    fragmentTransaction.commit()
                                                    fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)

                                                    tabLayoutHeater.visibility = View.VISIBLE
                                                    viewPagerHeater.visibility = View.VISIBLE

                                                    tabLayoutHeater.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                                    viewPagerHeater.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                                }
                                            }

                                            fabDeviceSettings.visibility = View.VISIBLE
                                            fabDeleteDevice.visibility = View.VISIBLE

                                            if (selectedDeviceId != 5) frameLayoutDevices.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                            fabDeviceSettings.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                            fabDeleteDevice.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                        }
                                    }

                                    override fun onAnimationCancel(animation: Animator) {}
                                    override fun onAnimationRepeat(animation: Animator) {}

                                }).start()
                        }
                    }
                } else {
                    recyclerViewAllDevices.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                    fabBack.animate().alpha(0f).setDuration(500).setStartDelay(0)
                        .setListener(object : Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    recyclerViewAllDevices.visibility = View.GONE
                                    fabBack.visibility = View.GONE

                                    layoutLogo.visibility = View.VISIBLE
                                    buttonAddDevice.visibility = View.VISIBLE

                                    buttonAddDevice.translationX = 1100f
                                    layoutLogo.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    buttonAddDevice.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                }
            }

            fabDeviceSettings.setOnClickListener {
                vibrate()

                if (!(selectedDeviceId == 2 && selectedRemoteId >= 1 && selectedRemoteId <= 3)) {
                    deviceSettingsMode = true
                    editPreferences.putInt("DeviceId", selectedDeviceId).apply()

                    var isDeviceSettingsAnimationStarted = true
                    if (selectedDeviceId != 5) {
                        frameLayoutDevices.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                    } else {
                        tabLayoutHeater.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                        viewPagerHeater.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                    }
                    radioGroupHeatingOrBoiler.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                    fabDeviceSettings.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                    fabDeleteDevice.animate().alpha(0f).setDuration(500).setStartDelay(0)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isDeviceSettingsAnimationStarted) {
                                    isDeviceSettingsAnimationStarted = false

                                    radioGroupHeatingOrBoiler.visibility = View.GONE
                                    tabLayoutHeater.visibility = View.GONE
                                    viewPagerHeater.visibility = View.GONE
                                    fabDeviceSettings.visibility = View.GONE
                                    fabDeleteDevice.visibility = View.GONE
                                    frameLayoutDevices.visibility = View.VISIBLE

                                    deviceFragmentManager.beginTransaction().replace(R.id.frameLayoutDevices, DeviceSettingsFragment()).commit()
                                    frameLayoutDevices.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                } else {
                    remoteSettingsMode = true
                    editPreferences.putBoolean("OpenRemoteSettingsMode", true).apply()

                    var isAnimationStarted = true
                    fabDeviceSettings.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                    fabDeleteDevice.animate().alpha(0f).setDuration(500).setStartDelay(0)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    fabDeviceSettings.visibility = View.GONE
                                    fabDeleteDevice.visibility = View.GONE
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                }
            }

            fabDeleteDevice.setOnClickListener {
                vibrate()
                if (isNetworkConnected(requireActivity())) {
                    val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
                    alertDialogBuilder.setTitle("Удаление устройства")
                    alertDialogBuilder.setMessage("Вы точно хотите удалить устройство?")
                    alertDialogBuilder.setPositiveButton("Подтвердить") { _, _ ->
                        vibrate()

                        isDeviceSelected = false
                        addedDevicesArrayList.removeAt(adapterPosition)
                        addedDevicesRecyclerAdapter.notifyDataSetChanged()
                        if (addedDevicesArrayList.size >= 1) {
                            editPreferences.putString("DevicesArrayList", gson.toJson(addedDevicesArrayList)).apply()
                        } else {
                            editPreferences.putString("DevicesArrayList", "").apply()
                        }

                        var isAnimationStarted = true
                        if (selectedDeviceId != 5) {
                            frameLayoutDevices.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                        } else {
                            tabLayoutHeater.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                            viewPagerHeater.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                        }
                        if (selectedDeviceId == 1) radioGroupHeatingOrBoiler.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                        fabBack.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                        fabDeviceSettings.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                        fabDeleteDevice.animate().alpha(0f).setDuration(500).setStartDelay(0)
                            .setListener(object: Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}

                                override fun onAnimationEnd(animation: Animator) {
                                    if (isAnimationStarted) {
                                        isAnimationStarted = false

                                        frameLayoutDevices.visibility = View.GONE
                                        tabLayoutHeater.visibility = View.GONE
                                        viewPagerHeater.visibility = View.GONE
                                        radioGroupHeatingOrBoiler.visibility = View.GONE
                                        fabBack.visibility = View.GONE
                                        fabDeviceSettings.visibility = View.GONE
                                        fabDeleteDevice.visibility = View.GONE

                                        removeFragment()

                                        if (addedDevicesArrayList.size >= 1) {
                                            recyclerViewAddedDevices.visibility = View.VISIBLE
                                            fabAddDevice.visibility = View.VISIBLE

                                            recyclerViewAddedDevices.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                            fabAddDevice.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                        } else {
                                            layoutLogo.visibility = View.VISIBLE
                                            buttonAddDevice.visibility = View.VISIBLE

                                            buttonAddDevice.translationX = 1100f
                                            layoutLogo.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                            buttonAddDevice.animate().alpha(1f).translationX(0f).setDuration(500).setStartDelay(0).start()
                                        }

                                        with(realtimeDatabase.child(firebaseAuth.currentUser!!.uid)) {
                                            when (selectedDeviceId) {
                                                0 -> {
                                                    editPreferences.remove("TemperatureEntriesArrayList")
                                                    editPreferences.remove("HumidityEntriesArrayList")
                                                    editPreferences.remove("WiFiThermometerTimestampsArrayList").commit()

                                                    child("WiFiThermometer").child("temperatureHumidity").removeValue()
                                                    child("WiFiThermometer").child("batteryLevel").removeValue()
                                                }
                                                1 -> {
                                                    heatingAndBoilerNodesList.forEach {
                                                        child("HeatingAndBoiler").child(it).removeValue()
                                                    }
                                                }
                                                2 -> {
                                                    smartRemotesNodesList.forEach {
                                                        child("SmartRemotes").child(it).removeValue()
                                                    }
                                                }
                                                3 -> {
                                                    if (sharedPreferences.getString("PhotosArrayList", "") != "") {
                                                        val photosArrayList = gson.fromJson<ArrayList<Photo>>(
                                                            sharedPreferences.getString("PhotosArrayList", ""),
                                                            object : TypeToken<ArrayList<Photo?>?>() {}.type
                                                        )
                                                        photosArrayList.forEach { photo ->
                                                            Firebase.storage.getReferenceFromUrl(photo.photoUrl).delete()
                                                                .addOnFailureListener {
                                                                    Toast.makeText(requireActivity(), "Не удалось удалить фото ${photo.photoName}!", Toast.LENGTH_LONG).show()
                                                                }
                                                        }
                                                    }
                                                    editPreferences.remove("PhotosArrayList").commit()
                                                    child("SmartDoorbell").child("photoUrl").removeValue()
                                                    child("SmartDoorbell").child("settings").removeValue()
                                                }
                                                4 -> {
                                                    editPreferences.remove("MeterReadingsArrayList")
                                                    editPreferences.remove("MeterReadingsTimeArrayList").commit()

                                                    child("MeterReader").addChildEventListener(object: ChildEventListener {
                                                        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                                                            child("MeterReader").child(snapshot.key!!).removeValue()
                                                        }

                                                        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                                                        override fun onChildRemoved(snapshot: DataSnapshot) {}
                                                        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

                                                        override fun onCancelled(error: DatabaseError) {
                                                            Toast.makeText(requireActivity(), "Не удалось удалить данные устройства из Firebase!", Toast.LENGTH_LONG).show()
                                                        }

                                                    })
                                                }
                                                5 -> {
                                                    heaterNodesList.forEach {
                                                        child("Heater").child(it).removeValue()
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                }

                                override fun onAnimationCancel(animation: Animator) {}
                                override fun onAnimationRepeat(animation: Animator) {}

                            }).start()
                    }
                    alertDialogBuilder.setNegativeButton("Отмена") { _, _ ->
                        vibrate()
                    }
                    alertDialogBuilder.create().show()
                } else {
                    Toast.makeText(requireActivity(), "Нет подключения\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }

            radioGroupHeatingOrBoiler.setOnCheckedChangeListener { _, checkedId ->
                if (!radioButtonCheckedProgrammatically) {
                    vibrate()
                    when (checkedId) {
                        R.id.buttonHeating -> {
                            buttonHeating.setTextColor(Color.parseColor("#FFFFFFFF"))
                            buttonBoiler.setTextColor(Color.parseColor("#6A61AD"))
                        }
                        R.id.buttonBoiler -> {
                            buttonHeating.setTextColor(Color.parseColor("#6A61AD"))
                            buttonBoiler.setTextColor(Color.parseColor("#FFFFFFFF"))
                        }
                    }

                    var isAnimationStarted = true
                    frameLayoutDevices.animate().alpha(0f).setDuration(500).setStartDelay(0)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    when (checkedId) {
                                        R.id.buttonHeating -> {
                                            heatingOrBoilerFragment = false
                                            deviceFragmentManager.beginTransaction().replace(R.id.frameLayoutDevices, HeatingModeFragment()).commit()
                                        }
                                        R.id.buttonBoiler -> {
                                            heatingOrBoilerFragment = true
                                            deviceFragmentManager.beginTransaction().replace(R.id.frameLayoutDevices, BoilerModeFragment()).commit()
                                        }
                                    }
                                    frameLayoutDevices.animate().alpha(1f).setDuration(500).setStartDelay(100).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        startDelay = requireArguments().getLong("StartDelay")
        binding = FragmentDevicesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    private fun removeFragment() {
        val fragmentTransaction = deviceFragmentManager.beginTransaction()
        when(selectedDeviceId) {
            0 -> {
                fragmentTransaction.remove(WiFiThermometerFragment())
            }
            1 -> {
                if (heatingOrBoilerFragment) {
                    fragmentTransaction.remove(HeatingModeFragment())
                } else {
                    fragmentTransaction.remove(BoilerModeFragment())
                }
            }
            2 -> {
                fragmentTransaction.remove(SmartRemotesFragment())
            }
            3 -> {
                fragmentTransaction.remove(SmartDoorbellFragment())
            }
            4 -> {
                fragmentTransaction.remove(MeterReaderFragment())
            }
        }
        if (selectedDeviceId != 5) {
            fragmentTransaction.commit()
            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
        }
    }

    private val allDevicesRecyclerAdapterClickListener = object: DevicesRecyclerAdapter.OnItemClickListener {
        @SuppressLint("NotifyDataSetChanged")
        override fun onItemClick(position: Int) {
            vibrate()
            with(binding) {
                if (!addedDevicesArrayList.contains(allDevicesList[position]) && addedDevicesArrayList.size < 6) {
                    addedDevicesArrayList.add(allDevicesList[position])
                    editPreferences.putString("DevicesArrayList", gson.toJson(addedDevicesArrayList)).apply()
                    
                    with(realtimeDatabase.child(firebaseAuth.currentUser!!.uid)) {
                        when (position) {
                            0 -> {
                                child("WiFiThermometer").child("temperatureHumidity").setValue("0 0")
                                child("WiFiThermometer").child("batteryLevel").setValue(0)
                            }
                            1 -> {
                                val nodeValuesList = listOf(false, 1, false, 0, 0, " ", " ", " ", " ", 0, " ")
                                heatingAndBoilerNodesList.forEach {
                                    child("HeatingAndBoiler").child(it).setValue(nodeValuesList[heatingAndBoilerNodesList.indexOf(it)])
                                }
                            }
                            2 -> {
                                val nodeValuesList = listOf(" ", " ", " ", " ", " ", " ")
                                smartRemotesNodesList.forEach {
                                    child("SmartRemotes").child(it).setValue(nodeValuesList[smartRemotesNodesList.indexOf(it)])
                                }
                            }
                            3 -> {
                                child("SmartDoorbell").child("photoUrl").setValue(" ")
                                child("SmartDoorbell").child("settings").setValue(" ")
                            }
                            4 -> {
                                child("MeterReader").child("ignoreNode").setValue(false)
                            }
                            5 -> {
                                val nodeValuesList = listOf(false, " ", 0, " ", " ")
                                heaterNodesList.forEach {
                                    child("Heater").child(it).setValue(nodeValuesList[heaterNodesList.indexOf(it)])
                                }
                            }
                            else -> {}
                        }
                    }

                    if (addedDevicesArrayList.size == 1) {
                        addedDevicesRecyclerAdapter = DevicesRecyclerAdapter(addedDevicesArrayList)
                        addedDevicesRecyclerAdapter.setOnItemClickListener(addedDevicesRecyclerAdapterClickListener)
                        recyclerViewAddedDevices.apply {
                            adapter = addedDevicesRecyclerAdapter
                            layoutManager = GridLayoutManager(requireActivity(), 2)
                            addItemDecoration(DeviceItemDecoration())
                        }
                    } else {
                        addedDevicesRecyclerAdapter.notifyDataSetChanged()
                    }

                    var isAnimationStarted = true
                    recyclerViewAllDevices.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                    fabBack.animate().alpha(0f).setDuration(500).setStartDelay(0)
                        .setListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                if (isAnimationStarted) {
                                    isAnimationStarted = false

                                    recyclerViewAllDevices.visibility = View.GONE
                                    fabBack.visibility = View.GONE

                                    recyclerViewAddedDevices.visibility = View.VISIBLE
                                    fabAddDevice.visibility = View.VISIBLE

                                    recyclerViewAddedDevices.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    fabAddDevice.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}

                        }).start()
                    Toast.makeText(requireActivity(), "Устройство добавлено!", Toast.LENGTH_SHORT).show()
                } else {
                    if (addedDevicesArrayList.size == 6) {
                        Toast.makeText(requireActivity(), "Вы добавили максимальное количество устройств!", Toast.LENGTH_LONG).show()
                    } else if (addedDevicesArrayList.size < 6 && addedDevicesArrayList.contains(allDevicesList[position])) {
                        Toast.makeText(requireActivity(), "Вы уже добавили\nэто устройство!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private val addedDevicesRecyclerAdapterClickListener = object: DevicesRecyclerAdapter.OnItemClickListener {
        override fun onItemClick(position: Int) {
            vibrate()
            with(binding ) {
                isDeviceSelected = true
                lateinit var fragment: Fragment
                when (addedDevicesArrayList[position].deviceName) {
                    getString(R.string.text_wifi_thermometer) -> {
                        selectedDeviceId = 0
                        fragment = WiFiThermometerFragment()
                    }
                    getString(R.string.text_heating_and_boiler) -> {
                        selectedDeviceId = 1
                        fragment = HeatingModeFragment()
                    }
                    getString(R.string.text_smart_ir_remote) -> {
                        selectedDeviceId = 2
                        fragment = SmartRemotesFragment()
                    }
                    getString(R.string.text_smart_doorbell) -> {
                        selectedDeviceId = 3
                        fragment = SmartDoorbellFragment()
                    }
                    getString(R.string.text_meter_reader) -> {
                        selectedDeviceId = 4
                        fragment = MeterReaderFragment()
                    }
                    getString(R.string.text_smart_heater) -> {
                        selectedDeviceId = 5
                        val fragmentList = mutableListOf(HeaterTemperatureFragment(), HeaterTimeFragment())
                        val adapter = ViewPagerAdapter(requireActivity(), fragmentList)
                        viewPagerHeater.adapter = adapter
                        TabLayoutMediator(tabLayoutHeater, viewPagerHeater) { tab, position ->
                            if (position == 0) {
                                tab.text = getString(R.string.tab_item_temperature_mode)
                            } else {
                                tab.text = getString(R.string.tab_item_time_mode)
                            }
                        }.attach()
                    }
                }
                adapterPosition = position

                if (selectedDeviceId != 5) deviceFragmentManager.beginTransaction().replace(R.id.frameLayoutDevices, fragment).commit()

                var isAnimationStarted = true
                recyclerViewAddedDevices.animate().alpha(0f).setDuration(500).setStartDelay(0).start()
                fabAddDevice.animate().alpha(0f).setDuration(500).setStartDelay(0)
                    .setListener(object: Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {}

                        override fun onAnimationEnd(animation: Animator) {
                            if (isAnimationStarted) {
                                isAnimationStarted = false

                                recyclerViewAddedDevices.visibility = View.GONE
                                fabAddDevice.visibility = View.GONE

                                if (selectedDeviceId != 5) {
                                    frameLayoutDevices.visibility = View.VISIBLE
                                } else {
                                    tabLayoutHeater.visibility = View.VISIBLE
                                    viewPagerHeater.visibility = View.VISIBLE
                                }
                                if (selectedDeviceId == 1) radioGroupHeatingOrBoiler.visibility = View.VISIBLE
                                if (selectedDeviceId != 4) fabDeviceSettings.visibility = View.VISIBLE
                                fabBack.visibility = View.VISIBLE
                                fabDeleteDevice.visibility = View.VISIBLE

                                if (selectedDeviceId != 5) {
                                    frameLayoutDevices.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    if (selectedDeviceId == 1) radioGroupHeatingOrBoiler.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                } else {
                                    tabLayoutHeater.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                    viewPagerHeater.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                }
                                fabBack.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                if (selectedDeviceId != 4) fabDeviceSettings.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                                fabDeleteDevice.animate().alpha(1f).setDuration(500).setStartDelay(0).start()
                            }
                        }

                        override fun onAnimationCancel(animation: Animator) {}
                        override fun onAnimationRepeat(animation: Animator) {}

                    }).start()
            }
        }

    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (isHapticFeedbackEnabled == "1") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    binding.buttonAddDevice.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } else {
                    binding.buttonAddDevice.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(20)
                }
            }
        }
    }
}