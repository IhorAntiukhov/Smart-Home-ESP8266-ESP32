package com.arduinoworld.smarthome

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arduinoworld.smarthome.databinding.HeatingTimeRecyclerViewItemBinding

class HeatingTimeRecyclerAdapter (private val heatingTimestampsList: List<HeatingOrBoilerTimestamp>)
    : RecyclerView.Adapter<HeatingTimeRecyclerAdapter.ViewHolder>() {

    private lateinit var clickListener: OnItemClickListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = HeatingTimeRecyclerViewItemBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, clickListener)
    }

    override fun getItemCount(): Int = heatingTimestampsList.size

    class ViewHolder(val binding: HeatingTimeRecyclerViewItemBinding, listener: OnItemClickListener) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION)
                    listener.onItemClick(position)
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder) {
            with(heatingTimestampsList[position]) {
                binding.textTime.text = time
                binding.textTimeHeatingElements.text = binding.textTimeHeatingElements.context.getString(
                    R.string.heating_elements_text, heatingElements)
                if (onOff) {
                    binding.cardViewTime.setCardBackgroundColor(android.graphics.Color.parseColor("#5347AE"))
                } else {
                    binding.cardViewTime.setCardBackgroundColor(android.graphics.Color.parseColor("#6A61AD"))
                }
            }
        }
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        clickListener = listener
    }
}