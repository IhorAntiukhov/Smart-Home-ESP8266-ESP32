package com.arduinoworld.smarthome

import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arduinoworld.smarthome.databinding.HeatingTimeRecyclerViewItemBinding

class HeatingTimeRecyclerAdapter (private val heatingTimestampsList: List<HeatingOrBoilerTimestamp>)
    : RecyclerView.Adapter<HeatingTimeRecyclerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = HeatingTimeRecyclerViewItemBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val heaterTimestamp: HeatingOrBoilerTimestamp = heatingTimestampsList[position]
        holder.bind(heaterTimestamp)
    }

    override fun getItemCount(): Int = heatingTimestampsList.size

    class ViewHolder(private val binding: HeatingTimeRecyclerViewItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(heaterTimestamp: HeatingOrBoilerTimestamp) {
            binding.textTime.text = heaterTimestamp.time
            binding.textTimeHeatingElements.text = binding.textTimeHeatingElements.context.getString(R.string.heating_elements_text, heaterTimestamp.heatingElements)
            if (heaterTimestamp.onOff) {
                binding.cardViewTime.setCardBackgroundColor(Color.parseColor("#5347AE"))
            } else {
                binding.cardViewTime.setCardBackgroundColor(Color.parseColor("#6A61AD"))
            }
        }
    }
}