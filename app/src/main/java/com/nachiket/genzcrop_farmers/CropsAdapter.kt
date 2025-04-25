package com.nachiket.genzcrop_farmers

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nachiket.genzcrop_farmers.data_class.Crop

class CropsAdapter(
    private val crops: MutableList<Pair<String, Crop>>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<CropsAdapter.CropViewHolder>() {

    private var filteredCrops = mutableListOf<Pair<String, Crop>>()

    init {
        filteredCrops.addAll(crops)
    }

    class CropViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cropImageView: ImageView = view.findViewById(R.id.cropImageView)
        val cropNameTv: TextView = view.findViewById(R.id.cropNameTv)
        val cropVarietyTv: TextView = view.findViewById(R.id.cropVarietyTv)
        val cropGradeTv: TextView = view.findViewById(R.id.cropGradeTv)
        val cropQuantityTv: TextView = view.findViewById(R.id.cropQuantityTv)
        val cropPriceTv: TextView = view.findViewById(R.id.cropPriceTv)
        val readyDateTv: TextView = view.findViewById(R.id.readyDateTv)
        val deleteCropBtn: ImageButton = view.findViewById(R.id.deleteCropBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CropViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.crop_item, parent, false)
        return CropViewHolder(view)
    }

    override fun onBindViewHolder(holder: CropViewHolder, position: Int) {
        val (key, crop) = filteredCrops[position]

        // Use the key as the crop name
        holder.cropNameTv.text = key
        holder.cropVarietyTv.text = "Variety: ${crop.variety}"
        holder.cropGradeTv.text = "Grade: ${crop.grade}"
        holder.cropQuantityTv.text = "Quantity: ${crop.quantity}kg"
        holder.cropPriceTv.text = "Price: ₹${crop.price}"
        holder.readyDateTv.text = "Ready Date: ${crop.ready_date}"

        // Load image using Glide
        if (crop.image1.isNotEmpty()) {
            Glide.with(holder.cropImageView.context)
                .load(crop.image1)
                .placeholder(R.drawable.sample_plant)
                .error(R.drawable.sample_plant)
                .into(holder.cropImageView)
        } else {
            holder.cropImageView.setImageResource(R.drawable.sample_plant)
        }

        holder.deleteCropBtn.setOnClickListener {
            onDeleteClick(key)
        }
    }

    override fun getItemCount() = filteredCrops.size

    fun filter(query: String) {
        filteredCrops.clear()
        if (query.isEmpty()) {
            filteredCrops.addAll(crops)
        } else {
            crops.forEach { (key, crop) ->
                if (key.contains(query, ignoreCase = true) ||  // Search by crop name (key)
                    crop.variety.contains(query, ignoreCase = true)) {
                    filteredCrops.add(key to crop)
                }
            }
        }
        notifyDataSetChanged()
    }

    fun updateCrops(newCrops: List<Pair<String, Crop>>) {
        crops.clear()
        crops.addAll(newCrops)
        filter("")
    }
}