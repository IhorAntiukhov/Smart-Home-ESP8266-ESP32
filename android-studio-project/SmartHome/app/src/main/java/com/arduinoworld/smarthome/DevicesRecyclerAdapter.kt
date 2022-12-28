package com.arduinoworld.smarthome

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arduinoworld.smarthome.databinding.DevicesRecyclerViewItemBinding

class DevicesRecyclerAdapter (private val devicesList : List<Device>)
    : RecyclerView.Adapter<DevicesRecyclerAdapter.ViewHolder>() {

    private lateinit var clickListener: OnItemClickListener

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val binding = DevicesRecyclerViewItemBinding
            .inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
        return ViewHolder(binding, clickListener)
    }

    class ViewHolder(val binding: DevicesRecyclerViewItemBinding, listener: OnItemClickListener) : RecyclerView.ViewHolder(binding.root) {
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
            with(devicesList[position]) {
                binding.imageDeviceLogo.setImageResource(deviceImage)
                binding.textDeviceName.text = deviceName
            }
        }
    }

    override fun getItemCount() = devicesList.size

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        clickListener = listener
    }
}