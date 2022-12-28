package com.arduinoworld.smarthome

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arduinoworld.smarthome.databinding.PhotosRecyclerViewItemBinding
import com.bumptech.glide.Glide

class PhotosRecyclerAdapter (private val photosList : List<Photo>)
    : RecyclerView.Adapter<PhotosRecyclerAdapter.ViewHolder>() {

    private lateinit var clickListener: OnItemClickListener

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val binding = PhotosRecyclerViewItemBinding
            .inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
        return ViewHolder(binding, clickListener)
    }

    class ViewHolder(val binding: PhotosRecyclerViewItemBinding, listener: OnItemClickListener) : RecyclerView.ViewHolder(binding.root) {
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
            with(photosList[position]) {
                binding.textPhotoName.text = photoName
                Glide.with(holder.itemView).load(photoUrl).into(binding.imageView)
            }
        }
    }

    override fun getItemCount() = photosList.size

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        clickListener = listener
    }
}