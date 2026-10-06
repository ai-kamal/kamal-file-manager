package com.kovak.kamal

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class FileViewModel(private val manager: KamalFileManager) : ViewModel() {

    private val _files = MutableLiveData<List<FileItem>>(emptyList())
    val files: LiveData<List<FileItem>> = _files

    private val _currentPath = MutableLiveData("/sdcard")
    val currentPath: LiveData<String> = _currentPath

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _toastMsg = MutableLiveData<String?>(null)
    val toastMsg: LiveData<String?> = _toastMsg

    // Clipboard for copy/paste
    private var clipboardPaths = mutableListOf<String>()
    private var clipboardMode = ClipMode.NONE

    val breadcrumbs: List<String>
        get() {
            val path = _currentPath.value ?: "/sdcard"
            val parts = path.split("/").filter { it.isNotEmpty() }
            val crumbs = mutableListOf<String>()
            var accumulated = ""
            for (part in parts) {
                accumulated += "/$part"
                crumbs.add(accumulated)
            }
            return crumbs
        }

    fun loadPath(path: String) {
        _currentPath.value = path
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = manager.listFiles(path)
            _files.value = result
            _loading.value = false
            if (result.isEmpty() && path != "/sdcard") {
                _error.value = "Empty folder or access denied"
            }
        }
    }

    fun navigateUp(): Boolean {
        val current = _currentPath.value ?: return false
        val parent = current.substringBeforeLast("/")
        if (parent.isBlank() || parent == current) return false
        loadPath(parent)
        return true
    }

    fun refresh() {
        loadPath(_currentPath.value ?: "/sdcard")
    }

    fun copyToClipboard(paths: List<String>) {
        clipboardPaths = paths.toMutableList()
        clipboardMode = ClipMode.COPY
        _toastMsg.value = "${paths.size} item(s) copied"
    }

    fun cutToClipboard(paths: List<String>) {
        clipboardPaths = paths.toMutableList()
        clipboardMode = ClipMode.CUT
        _toastMsg.value = "${paths.size} item(s) cut"
    }

    fun paste() {
        if (clipboardPaths.isEmpty()) {
            _toastMsg.value = "Nothing to paste"
            return
        }
        val destDir = _currentPath.value ?: return
        viewModelScope.launch {
            _loading.value = true
            var successCount = 0
            clipboardPaths.forEach { src ->
                val fileName = src.substringAfterLast("/")
                val dest = "$destDir/$fileName"
                val ok = when (clipboardMode) {
                    ClipMode.COPY -> manager.copy(src, dest)
                    ClipMode.CUT -> manager.move(src, dest)
                    ClipMode.NONE -> false
                }
                if (ok) successCount++
            }
            if (clipboardMode == ClipMode.CUT) {
                clipboardPaths.clear()
                clipboardMode = ClipMode.NONE
            }
            _loading.value = false
            _toastMsg.value = "Pasted $successCount item(s)"
            refresh()
        }
    }

    fun delete(paths: List<String>) {
        viewModelScope.launch {
            _loading.value = true
            var count = 0
            paths.forEach { path ->
                if (manager.delete(path)) count++
            }
            _loading.value = false
            _toastMsg.value = "Deleted $count item(s)"
            refresh()
        }
    }

    fun createFolder(name: String) {
        val base = _currentPath.value ?: return
        val newPath = "$base/$name"
        viewModelScope.launch {
            val ok = manager.createFolder(newPath)
            _toastMsg.value = if (ok) "Folder created" else "Failed to create folder"
            if (ok) refresh()
        }
    }

    fun rename(oldPath: String, newName: String) {
        val parentDir = oldPath.substringBeforeLast("/")
        val newPath = "$parentDir/$newName"
        viewModelScope.launch {
            val ok = manager.move(oldPath, newPath)
            _toastMsg.value = if (ok) "Renamed" else "Rename failed"
            if (ok) refresh()
        }
    }

    fun hasClipboard() = clipboardPaths.isNotEmpty()

    enum class ClipMode { NONE, COPY, CUT }
}
