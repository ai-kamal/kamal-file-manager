package com.kovak.kamal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class FileViewModelFactory(private val manager: KamalFileManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FileViewModel(manager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
