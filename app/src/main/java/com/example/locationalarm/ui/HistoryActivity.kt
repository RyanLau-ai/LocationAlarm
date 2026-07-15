package com.example.locationalarm.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.locationalarm.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var viewModel: AlarmViewModel
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = "触发历史"

        viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]

        adapter = HistoryAdapter(onDelete = { history ->
            viewModel.deleteHistoryById(history.id)
        })

        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewHistory.adapter = adapter

        viewModel.allHistory.observe(this) { history ->
            adapter.submitList(history)
            binding.tvEmptyHistory.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清除历史")
                .setMessage("确定要清除所有触发历史记录吗？此操作不可撤销。")
                .setPositiveButton("清除") { _, _ ->
                    viewModel.deleteAllHistory()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
}
