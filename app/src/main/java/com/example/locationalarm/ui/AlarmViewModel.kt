package com.example.locationalarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.locationalarm.LocationAlarmApp
import com.example.locationalarm.data.Alarm
import com.example.locationalarm.data.AlarmHistory
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as LocationAlarmApp).repository

    val allAlarms: LiveData<List<Alarm>> = repository.getAllAlarms()
    val allHistory: LiveData<List<AlarmHistory>> = repository.getAllHistory()
    val allTags: LiveData<List<String>> = repository.getAllTags()

    fun insertAlarm(alarm: Alarm) = viewModelScope.launch {
        repository.insert(alarm)
    }

    fun updateAlarm(alarm: Alarm) = viewModelScope.launch {
        repository.update(alarm)
    }

    fun deleteAlarm(alarm: Alarm) = viewModelScope.launch {
        repository.delete(alarm)
    }

    fun deleteAlarmById(id: Long) = viewModelScope.launch {
        repository.deleteById(id)
    }

    fun setAlarmEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(id, enabled)
    }

    fun deleteHistoryById(id: Long) = viewModelScope.launch {
        repository.deleteHistoryById(id)
    }

    fun deleteAllHistory() = viewModelScope.launch {
        repository.deleteAllHistory()
    }

    suspend fun getAlarmById(id: Long): Alarm? = repository.getAlarmById(id)
}
