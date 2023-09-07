package com.example.diashield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch


class VitalViewModel(private val repository: VitalsRepository) : ViewModel() {
    fun insert(vitalsUser: VitalsDb.VitalsUser) = viewModelScope.launch {
        repository.insert(vitalsUser)
    }
}

class VitalViewModelFactory(private val repository: VitalsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VitalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VitalViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
