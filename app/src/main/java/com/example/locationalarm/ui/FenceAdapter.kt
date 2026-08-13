package com.example.locationalarm.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.locationalarm.R
import com.example.locationalarm.data.AlertWay
import com.example.locationalarm.data.GeoFence
import com.example.locationalarm.databinding.ItemFenceBinding

/**
 * 围栏列表适配器
 */
class FenceAdapter(
    private val onClick: (GeoFence) -> Unit
) : ListAdapter<GeoFence, FenceAdapter.FenceViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FenceViewHolder {
        val binding = ItemFenceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FenceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FenceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FenceViewHolder(
        private val binding: ItemFenceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(fence: GeoFence) {
            binding.tvName.text = fence.name
            binding.tvAddress.text = fence.address.ifBlank { "${fence.latitude}, ${fence.longitude}" }

            // 进入/离开提醒状态标签
            binding.tvInStatus.visibility = if (fence.enableIn) View.VISIBLE else View.GONE
            binding.tvOutStatus.visibility = if (fence.enableOut) View.VISIBLE else View.GONE

            // 提醒方式图标
            binding.ivAlertWay.setImageResource(
                when (fence.alertWay) {
                    AlertWay.VIBRATE -> R.drawable.ic_vibration
                    AlertWay.RING -> R.drawable.ic_ring
                    AlertWay.BOTH -> R.drawable.ic_both
                    AlertWay.SILENT -> R.drawable.ic_silent
                }
            )

            binding.root.setOnClickListener { onClick(fence) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<GeoFence>() {
            override fun areItemsTheSame(oldItem: GeoFence, newItem: GeoFence): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: GeoFence, newItem: GeoFence): Boolean {
                return oldItem == newItem
            }
        }
    }
}
