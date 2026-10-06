package com.kovak.kamal

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class FileViewModel(private val manager: KamalFileManager) : ViewModel() {

    // ── Public enums ─────────────────────────────────────────────
    enum class SortMode {
        NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_DESC, DATE_ASC, TYPE
    }

    enum class ClipMode { NONE, COPY, CUT }

    // ── Internal state ────────────────────────────────────────────
    private val rawFiles = mutableListOf<FileItem>()           // un-filtered, un-sorted
    var sortMode: SortMode = SortMode.NAME_ASC
        private set
    var searchQuery: String = ""
        private set

    // ── Exposed LiveData ──────────────────────────────────────────
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

    // ── Clipboard ─────────────────────────────────────────────────
    private var clipboardPaths = mutableListOf<String>()
    private var clipboardMode = ClipMode.NONE

    // ── Breadcrumbs ────────────────────────────────────────────────
    val breadcrumbs: List<String>
        get() {
            val path = _currentPath.value ?: "/sdcard"
            val parts = path.split("/").filter { it.isNotEmpty() }
            val crumbs = mutableListOf<String>()
            var acc = ""
            for (part in parts) { acc += "/$part"; crumbs.add(acc) }
            return crumbs
        }

    // ── Navigation ────────────────────────────────────────────────
    fun loadPath(path: String) {
        _currentPath.value = path
        searchQuery = ""          // clear search on folder change
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = manager.listFiles(path)
            rawFiles.clear()
            rawFiles.addAll(result)
            applyFilterAndSort()
            _loading.value = false
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
        viewModelScope.launch {
            _loading.value = true
            val result = manager.listFiles(_currentPath.value ?: "/sdcard")
            rawFiles.clear()
            rawFiles.addAll(result)
            applyFilterAndSort()
            _loading.value = false
        }
    }

    // ── Sort & search ─────────────────────────────────────────────
    fun setSort(mode: SortMode) {
        sortMode = mode
        applyFilterAndSort()
    }

    fun setSearch(query: String) {
        searchQuery = query
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        val q = searchQuery.lowercase().trim()
        val filtered = if (q.isEmpty()) rawFiles.toList()
                       else rawFiles.filter { it.name.lowercase().contains(q) }

        val sorted = when (sortMode) {
            SortMode.NAME_ASC  -> filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            SortMode.NAME_DESC -> filtered.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
            SortMode.SIZE_ASC  -> filtered.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.size })
            SortMode.SIZE_DESC -> filtered.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.size })
            SortMode.DATE_DESC -> filtered.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified })
            SortMode.DATE_ASC  -> filtered.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.lastModified })
            SortMode.TYPE      -> filtered.sortedWith(compareBy({ it.fileTypeIcon.ordinal }, { it.name.lowercase() }))
        }

        _files.value = sorted
    }

    // ── Clipboard ops ─────────────────────────────────────────────
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
        if (clipboardPaths.isEmpty()) { _toastMsg.value = "Nothing to paste"; return }
        val destDir = _currentPath.value ?: return
        viewModelScope.launch {
            _loading.value = true
            var successCount = 0
            clipboardPaths.forEach { src ->
                val fileName = src.substringAfterLast("/")
                val dest = "$destDir/$fileName"
                val ok = when (clipboardMode) {
                    ClipMode.COPY -> manager.copy(src, dest)
                    ClipMode.CUT  -> manager.move(src, dest)
                    ClipMode.NONE -> false
                }
                if (ok) successCount++
            }
            if (clipboardMode == ClipMode.CUT) { clipboardPaths.clear(); clipboardMode = ClipMode.NONE }
            _loading.value = false
            _toastMsg.value = "Pasted $successCount item(s)"
            refresh()
        }
    }

    fun delete(paths: List<String>) {
        viewModelScope.launch {
            _loading.value = true
            var count = 0
            paths.forEach { if (manager.delete(it)) count++ }
            _loading.value = false
            _toastMsg.value = "Deleted $count item(s)"
            refresh()
        }
    }

    fun createFolder(name: String) {
        val base = _currentPath.value ?: return
        viewModelScope.launch {
            val ok = manager.createFolder("$base/$name")
            _toastMsg.value = if (ok) "Folder created" else "Failed to create folder"
            if (ok) refresh()
        }
    }

    fun rename(oldPath: String, newName: String) {
        val parentDir = oldPath.substringBeforeLast("/")
        viewModelScope.launch {
            val ok = manager.move(oldPath, "$parentDir/$newName")
            _toastMsg.value = if (ok) "Renamed" else "Rename failed"
            if (ok) refresh()
        }
    }

    fun hasClipboard() = clipboardPaths.isNotEmpty()
}
