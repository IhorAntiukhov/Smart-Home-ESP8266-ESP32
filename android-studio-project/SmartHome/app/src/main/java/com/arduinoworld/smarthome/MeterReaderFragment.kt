package com.arduinoworld.smarthome

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.arduinoworld.smarthome.MainActivity.Companion.editPreferences
import com.arduinoworld.smarthome.MainActivity.Companion.firebaseAuth
import com.arduinoworld.smarthome.MainActivity.Companion.realtimeDatabase
import com.arduinoworld.smarthome.MainActivity.Companion.sharedPreferences
import com.arduinoworld.smarthome.databinding.FragmentMeterReaderBinding
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MeterReaderFragment : Fragment() {
    private lateinit var binding: FragmentMeterReaderBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            val gson = Gson()
            var meterReadingsArrayList = ArrayList<Entry>()
            var meterReadingsTimeArrayList = ArrayList<String>()
            val meterDigitsAfterPoint = sharedPreferences.getString("MeterDigitsAfterPoint", "2")!!.toInt()
            val maxStorageTimeOfMeterReadings = sharedPreferences.getString("MaxStorageTimeOfMeterReadings", "7")!!.toInt()

            with(graph) {
                setBackgroundColor(Color.TRANSPARENT)
                setNoDataText(getString(R.string.text_no_meter_readings_received).replace("/n", " "))
                setNoDataTextColor(Color.parseColor("#6A61AD"))
                extraTopOffset = 10F
                setTouchEnabled(true)
                isDragEnabled = true
                isScaleXEnabled = true
                isScaleYEnabled = false
                axisRight.isEnabled = false
                description.isEnabled = false
            }

            with(graph.xAxis) {
                isEnabled = true
                setDrawAxisLine(true)
                setDrawGridLines(true)
                isGranularityEnabled = true
                position = XAxis.XAxisPosition.TOP
                gridColor = Color.parseColor("#5347AE")
                textColor = Color.parseColor("#5347AE")
                gridLineWidth = 1.5F
                granularity = 1f
                textSize = 12F
            }

            with(graph.axisLeft) {
                isEnabled = true
                setDrawAxisLine(false)
                setDrawGridLines(true)
                gridColor = Color.parseColor("#5347AE")
                textColor = Color.parseColor("#5347AE")
                gridLineWidth = 1.5F
                textSize = 12F
            }

            var i = 0
            if (sharedPreferences.getString("MeterReadingsArrayList", "") != "") {
                meterReadingsArrayList = gson.fromJson(
                    sharedPreferences.getString("MeterReadingsArrayList", ""),
                    object : TypeToken<ArrayList<Entry?>?>() {}.type
                )
                meterReadingsTimeArrayList = gson.fromJson(
                    sharedPreferences.getString("MeterReadingsTimeArrayList", ""),
                    object : TypeToken<ArrayList<String?>?>() {}.type
                )
                layoutNoMeterReadingsReceived.visibility = View.GONE
                graph.visibility = View.VISIBLE
                i = meterReadingsArrayList.size
            }

            val firebaseMeterReadingsValueArrayList = ArrayList<String>()
            val firebaseMeterReadingsTimeArrayList = ArrayList<String>()
            val firebaseMeterReadingsDateArrayList = ArrayList<Date>()

            val firebaseMeterReadingsAllDateArrayList = ArrayList<Date>()
            val firebaseMeterReadingsArrayList = ArrayList<ArrayList<String>>()

            firebaseMeterReadingsArrayList.add(ArrayList())
            firebaseMeterReadingsArrayList.add(ArrayList())
            firebaseMeterReadingsArrayList.add(ArrayList())
            firebaseMeterReadingsArrayList.add(ArrayList())

            val dateFormat = SimpleDateFormat("HH:mm dd.MM", Locale.US)
            val calendar = Calendar.getInstance()

            realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child("MeterReader").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.childrenCount >= 2L) {
                        layoutNoMeterReadingsReceived.visibility = View.GONE
                        graph.visibility = View.VISIBLE

                        snapshot.children.forEach {
                            if (it.key!! != "ignoreNode") {
                                var snapshotValue = it.getValue(String::class.java)!!
                                var rawSnapshotValue = snapshotValue
                                val meterReadingTime = it.key!!.replaceRange(2, 3, ":").replaceRange(8, 9, ".")

                                if (!snapshotValue.contains("N")) {
                                    if (!snapshotValue.contains(".")) {
                                        val numberAfterPoint = snapshotValue.substring(snapshotValue.length - meterDigitsAfterPoint, snapshotValue.length)
                                        snapshotValue = snapshotValue.substring(0, snapshotValue.length - meterDigitsAfterPoint) + "." + numberAfterPoint
                                    }
                                    while (snapshotValue.startsWith("0")) {
                                        snapshotValue = snapshotValue.removeRange(0, 1)
                                    }

                                    firebaseMeterReadingsValueArrayList.add(snapshotValue)
                                    firebaseMeterReadingsTimeArrayList.add(meterReadingTime)
                                    firebaseMeterReadingsDateArrayList.add(dateFormat.parse(meterReadingTime)!!)
                                }
                                if (!rawSnapshotValue.contains(".")) {
                                    val numberAfterPoint = rawSnapshotValue.substring(rawSnapshotValue.length - meterDigitsAfterPoint, rawSnapshotValue.length)
                                    rawSnapshotValue = rawSnapshotValue.substring(0, rawSnapshotValue.length - meterDigitsAfterPoint) + "." + numberAfterPoint
                                }
                                firebaseMeterReadingsArrayList[0].add(rawSnapshotValue)
                                firebaseMeterReadingsArrayList[1].add(meterReadingTime)
                                firebaseMeterReadingsAllDateArrayList.add(dateFormat.parse(meterReadingTime)!!)
                            }
                        }

                        if (firebaseMeterReadingsArrayList[0].size > 1) {
                            firebaseMeterReadingsAllDateArrayList.sort()
                            firebaseMeterReadingsAllDateArrayList.forEach {
                                calendar.time = it

                                var hours = calendar.get(Calendar.HOUR_OF_DAY).toString()
                                var minutes = calendar.get(Calendar.MINUTE).toString()
                                var days = calendar.get(Calendar.DAY_OF_MONTH).toString()
                                var month = (calendar.get(Calendar.MONTH) + 1).toString()

                                if (hours.toInt() < 10) hours = "0$hours"
                                if (minutes.toInt() < 10) minutes = "0$minutes"
                                if (days.toInt() < 10) days = "0$days"
                                if (month.toInt() < 10) month = "0$month"

                                val index = firebaseMeterReadingsArrayList[1].indexOf("$hours:$minutes $days.$month")
                                firebaseMeterReadingsArrayList[2].add(firebaseMeterReadingsArrayList[0][index])
                                firebaseMeterReadingsArrayList[3].add(firebaseMeterReadingsArrayList[1][index])
                            }
                        } else {
                            firebaseMeterReadingsArrayList[2].add(firebaseMeterReadingsArrayList[0][0])
                            firebaseMeterReadingsArrayList[3].add(firebaseMeterReadingsArrayList[1][0])
                        }

                        var meterReadingsChecked = false
                        if (firebaseMeterReadingsValueArrayList.size >= 1 || meterReadingsArrayList.size >= 1) {
                            firebaseMeterReadingsDateArrayList.sort()
                            firebaseMeterReadingsDateArrayList.forEach {
                                calendar.time = it

                                var hours = calendar.get(Calendar.HOUR_OF_DAY).toString()
                                var minutes = calendar.get(Calendar.MINUTE).toString()
                                var days = calendar.get(Calendar.DAY_OF_MONTH).toString()
                                var month = (calendar.get(Calendar.MONTH) + 1).toString()

                                if (hours.toInt() < 10) hours = "0$hours"
                                if (minutes.toInt() < 10) minutes = "0$minutes"
                                if (days.toInt() < 10) days = "0$days"
                                if (month.toInt() < 10) month = "0$month"

                                val index = firebaseMeterReadingsTimeArrayList.indexOf("$hours:$minutes $days.$month")
                                meterReadingsArrayList.add(Entry(i.toFloat(), firebaseMeterReadingsValueArrayList[index].toFloat()))
                                meterReadingsTimeArrayList.add(firebaseMeterReadingsTimeArrayList[index])
                                i += 1
                            }

                            val meterReadingsValueArrayList = ArrayList<Float>()
                            meterReadingsArrayList.forEach {
                                meterReadingsValueArrayList.add(it.y)
                            }

                            val axisMinimumValue = meterReadingsArrayList[meterReadingsValueArrayList.indexOf(Collections.min(meterReadingsValueArrayList))].y
                            val axisMaximumValue = meterReadingsArrayList[meterReadingsValueArrayList.indexOf(Collections.max(meterReadingsValueArrayList))].y
                            if (axisMinimumValue != axisMaximumValue) {
                                val axisValuesDifference = axisMaximumValue - axisMinimumValue
                                graph.axisLeft.axisMinimum = (axisMinimumValue - axisValuesDifference * 0.1).toFloat()
                                graph.axisLeft.axisMaximum = (axisMaximumValue + axisValuesDifference * 0.1).toFloat()
                            } else {
                                graph.axisLeft.axisMinimum = (axisMaximumValue - axisMaximumValue * 0.25).toFloat()
                                graph.axisLeft.axisMaximum = (axisMaximumValue + axisMaximumValue * 0.25).toFloat()
                            }

                            val mainLegend = LegendEntry(getString(R.string.meter_readings_legend_label), Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.parseColor("#6A61AD"))
                            with(graph.legend) {
                                isEnabled = true
                                textSize = 14F
                                xEntrySpace = 5F
                                yEntrySpace = 5F
                                form = Legend.LegendForm.CIRCLE
                                horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                                setCustom(arrayOf(mainLegend))
                            }

                            val dataSet = LineDataSet(meterReadingsArrayList, getString(R.string.meter_readings_legend_label))
                            with(dataSet) {
                                lineWidth = 1.5F
                                circleRadius = 3F
                                valueTextSize = 12F
                                cubicIntensity = 1F
                                color = Color.parseColor("#6A61AD")
                                setCircleColor(Color.parseColor("#6A61AD"))
                                valueTextColor = Color.parseColor("#6A61AD")
                                setDrawCircleHole(false)
                                setDrawFilled(true)
                                fillDrawable = ContextCompat.getDrawable(requireActivity(), R.drawable.graph_filled_gradient)
                                valueFormatter = object : ValueFormatter() {
                                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                                        return meterReadingsArrayList.getOrNull(value.toInt())!!.y.toString()
                                    }
                                }
                            }

                            val dataSets = ArrayList<LineDataSet>()
                            dataSets.add(dataSet)
                            graph.data = LineData(dataSets as List<ILineDataSet>?)

                            graph.xAxis.valueFormatter = object : ValueFormatter() {
                                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                                    return meterReadingsTimeArrayList.getOrNull(value.toInt())
                                        ?: value.toString()
                                }
                            }

                            graph.invalidate()

                            if (meterReadingsArrayList.size > 1) {
                                if (axisMaximumValue != axisMinimumValue) {
                                    textMeterReadingChange.visibility = View.VISIBLE
                                    var meterReadingChange = (meterReadingsArrayList[meterReadingsArrayList.size - 1].y - meterReadingsArrayList[meterReadingsArrayList.size - 2].y).toString()
                                    val digitsAfterPoint = meterReadingChange.substring(meterReadingChange.indexOf(".") + 1, meterReadingChange.length).length
                                    if (digitsAfterPoint > meterDigitsAfterPoint) {
                                        meterReadingChange = meterReadingChange.substring(0, meterReadingChange.indexOf(".") + meterDigitsAfterPoint + 1)
                                    }
                                    if (meterReadingsArrayList.size > 1) textMeterReadingChange.text = getString(R.string.text_meter_reading_change, meterReadingChange)
                                }

                                val firstDate = dateFormat.parse(firebaseMeterReadingsArrayList[3][0])
                                val secondDate = dateFormat.parse(firebaseMeterReadingsArrayList[3][firebaseMeterReadingsArrayList[3].size - 1])

                                val different: Long = secondDate!!.time - firstDate!!.time

                                val secondsInMillis: Long = 1000
                                val minutesInMillis = secondsInMillis * 60
                                val hoursInMillis = minutesInMillis * 60
                                val daysInMillis = hoursInMillis * 24

                                if (different / daysInMillis >= maxStorageTimeOfMeterReadings) {
                                    meterReadingsChecked = true
                                    for (k in 0..firebaseMeterReadingsArrayList[2].size - 2) {
                                        realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child(firebaseMeterReadingsArrayList[3][k].replace(":", " ").replace(".", " ")).removeValue()
                                    }
                                    val meterReadingsArrayListWithoutLast = ArrayList<Entry>()
                                    val meterReadingsTimeArrayListWithoutLast = ArrayList<String>()
                                    for (k in 0..meterReadingsArrayList.size - 2) {
                                        meterReadingsArrayListWithoutLast.add(meterReadingsArrayList[k])
                                    }
                                    for (k in 0..meterReadingsTimeArrayList.size - 2) {
                                        meterReadingsTimeArrayListWithoutLast.add(meterReadingsTimeArrayList[k])
                                    }
                                    editPreferences.putString("MeterReadingsArrayList", gson.toJson(meterReadingsArrayListWithoutLast))
                                    editPreferences.putString("MeterReadingsTimeArrayList", gson.toJson(meterReadingsTimeArrayListWithoutLast)).apply()
                                }
                            }
                        }
                        if (!meterReadingsChecked) {
                            for (k in 0..firebaseMeterReadingsArrayList[2].size - 2) {
                                if (firebaseMeterReadingsArrayList[2][k].contains("N")) {
                                    realtimeDatabase.child(firebaseAuth.currentUser!!.uid).child(firebaseMeterReadingsArrayList[3][k].replace(":", " ").replace(".", " ")).removeValue()
                                }
                            }
                        }

                        textMeterReading.text = firebaseMeterReadingsArrayList[2][firebaseMeterReadingsArrayList[2].size - 1]
                        textLastMeterReadingTime.text = firebaseMeterReadingsArrayList[3][firebaseMeterReadingsArrayList[3].size - 1]
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMeterReaderBinding.inflate(layoutInflater, container, false)
        return binding.root
    }
}