package com.arduinoworld.smarthome

import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arduinoworld.smarthome.databinding.TimeRecyclerViewItemBinding

class TimeRecyclerAdapter (private val boilerTimestampsList: List<HeatingOrBoilerTimestamp>)
    : RecyclerView.Adapter<TimeRecyclerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = TimeRecyclerViewItemBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val heaterTimestamp: HeatingOrBoilerTimestamp = boilerTimestampsList[position]
        holder.bind(heaterTimestamp)
    }

    override fun getItemCount(): Int = boilerTimestampsList.size

    class ViewHolder(private val binding: TimeRecyclerViewItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(heaterTimestamp: HeatingOrBoilerTimestamp) {
            binding.textTime.text = heaterTimestamp.time
            if (heaterTimestamp.onOff) {
                binding.cardViewTime.setCardBackgroundColor(Color.parseColor("#5347AE"))
            } else {
                binding.cardViewTime.setCardBackgroundColor(Color.parseColor("#6A61AD"))
            }
        }
    }
}