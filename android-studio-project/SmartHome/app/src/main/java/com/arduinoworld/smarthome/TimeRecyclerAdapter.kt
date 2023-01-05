package com.arduinoworld.smarthome

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arduinoworld.smarthome.databinding.TimeRecyclerViewItemBinding

class TimeRecyclerAdapter (private val boilerTimestampsList: List<HeatingOrBoilerTimestamp>)
    : RecyclerView.Adapter<TimeRecyclerAdapter.ViewHolder>() {

    private lateinit var clickListener: OnItemClickListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = TimeRecyclerViewItemBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, clickListener)
    }

    override fun getItemCount(): Int = boilerTimestampsList.size

    class ViewHolder(val binding: TimeRecyclerViewItemBinding, listener: OnItemClickListener) : RecyclerView.ViewHolder(binding.root) {
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
            with(boilerTimestampsList[position]) {
                binding.textTime.text = time
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