package com.example.locationalarm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.locationalarm.data.Alarm
import com.example.locationalarm.databinding.ItemAlarmBinding

/**
 * Alarm list adapter with RecyclerView.
 * Switch listener is detached before setChecked to prevent recycle issues.
 */
class AlarmAdapter(
    private val onToggle: (Alarm) -> Unit,
    private val onEdit: (Alarm) -> Unit,
    private val onDelete: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val binding = ItemAlarmBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlarmViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlarmViewHolder(
        private val binding: ItemAlarmBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(alarm: Alarm) {
            binding.apply {
                tvAlarmName.text = alarm.name
                tvReminder.text = alarm.reminder
                tvAddress.text = alarm.address
                tvRadius.text = "${alarm.radius} 米"

                tvTag.text = if (alarm.tag.isNotEmpty()) "#${alarm.tag}" else ""
                tvTag.visibility = if (alarm.tag.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE

                if (alarm.triggered && alarm.enabled) {
                    tvStatus.text = "已触发"
                    tvStatus.visibility = android.view.View.VISIBLE
                } else if (!alarm.enabled) {
                    tvStatus.text = "已禁用"
                    tvStatus.visibility = android.view.View.VISIBLE
                } else {
                    tvStatus.visibility = android.view.View.GONE
                }

                if (alarm.repeatInterval > 0 && alarm.enabled) {
                    val minutes = alarm.repeatInterval / 60_000
                    tvRepeatInfo.text = if (minutes >= 60) {
                        "每 ${minutes / 60} 小时重复"
                    } else {
                        "每 $minutes 分钟重复"
                    }
                    tvRepeatInfo.visibility = android.view.View.VISIBLE
                } else {
                    tvRepeatInfo.visibility = android.view.View.GONE
                }

                switchEnabled.setOnCheckedChangeListener(null)
                switchEnabled.isChecked = alarm.enabled
                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    onToggle(alarm.copy(enabled = isChecked))
                }

                btnEdit.setOnClickListener { onEdit(alarm) }
                btnDelete.setOnClickListener { onDelete(alarm) }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Alarm>() {
        override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem == newItem
    }
}
