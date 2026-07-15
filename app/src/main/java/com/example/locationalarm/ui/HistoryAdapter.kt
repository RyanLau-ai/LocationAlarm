package com.example.locationalarm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.locationalarm.data.AlarmHistory
import com.example.locationalarm.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史记录适配器
 */
class HistoryAdapter(
    private val onDelete: (AlarmHistory) -> Unit
) : ListAdapter<AlarmHistory, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(history: AlarmHistory) {
            binding.apply {
                tvHistoryName.text = history.alarmName
                tvHistoryReminder.text = history.reminder
                tvHistoryAddress.text = history.address
                tvHistoryDistance.text = "距目标 ${history.distance.toInt()} 米"
                tvHistoryTime.text = dateFormat.format(Date(history.triggeredAt))
                btnDelete.setOnClickListener { onDelete(history) }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AlarmHistory>() {
        override fun areItemsTheSame(oldItem: AlarmHistory, newItem: AlarmHistory) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AlarmHistory, newItem: AlarmHistory) = oldItem == newItem
    }
}
