package com.kovak.kamal

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var rvFiles: RecyclerView
    private lateinit var rvBreadcrumb: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var fabNewFolder: FloatingActionButton
    private lateinit var tvStorageInfo: TextView

    private lateinit var fileAdapter: FileAdapter
    private lateinit var breadcrumbAdapter: BreadcrumbAdapter
    private lateinit var viewModel: FileViewModel
    private lateinit var manager: KamalFileManager

    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        rvFiles = findViewById(R.id.rvFiles)
        rvBreadcrumb = findViewById(R.id.rvBreadcrumb)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)
        fabNewFolder = findViewById(R.id.fabNewFolder)
        tvStorageInfo = findViewById(R.id.tvStorageInfo)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        manager = KamalFileManager(this)

        setupRecyclerViews()
        setupFab()
        initManager()
    }

    private fun initManager() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.text = "Connecting to Kamal File Manager..."
        tvEmpty.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = manager.init()
            when (result) {
                KamalFileManager.InitResult.SUCCESS -> {
                    val factory = FileViewModelFactory(manager)
                    viewModel = ViewModelProvider(this@MainActivity, factory)[FileViewModel::class.java]
                    observeViewModel()
                    viewModel.loadPath("/sdcard")
                    loadStorageInfo()
                }
                KamalFileManager.InitResult.DHIZUKU_NOT_AVAILABLE -> {
                    progressBar.visibility = View.GONE
                    showSetupDialog()
                }
                KamalFileManager.InitResult.PERMISSION_DENIED -> {
                    progressBar.visibility = View.GONE
                    showError("Permission denied. Grant device owner access via ADB.")
                }
                else -> {
                    progressBar.visibility = View.GONE
                    showError("Initialization failed. Run setup command and restart.")
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.files.observe(this) { files ->
            fileAdapter.submitList(files)
            tvEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
            if (files.isEmpty()) tvEmpty.text = "This folder is empty"
        }

        viewModel.loading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.currentPath.observe(this) {
            updateBreadcrumbs()
        }

        viewModel.toastMsg.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                tvEmpty.text = it
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun setupRecyclerViews() {
        fileAdapter = FileAdapter(
            context = this,
            onItemClick = { item ->
                if (item.isDirectory) viewModel.loadPath(item.path)
                else openFile(item)
            },
            onItemLongClick = { _, _ ->
                startSelectionMode()
                true
            }
        )

        rvFiles.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = fileAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        breadcrumbAdapter = BreadcrumbAdapter { path -> viewModel.loadPath(path) }

        rvBreadcrumb.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = breadcrumbAdapter
        }
    }

    private fun setupFab() {
        fabNewFolder.setOnClickListener { showCreateFolderDialog() }
    }

    private fun updateBreadcrumbs() {
        val crumbs = viewModel.breadcrumbs
        breadcrumbAdapter.submitList(crumbs)
        rvBreadcrumb.scrollToPosition(crumbs.size - 1)
    }

    private fun loadStorageInfo() {
        lifecycleScope.launch {
            val free = manager.getFreeSpace("/sdcard")
            val total = manager.getTotalSpace("/sdcard")
            tvStorageInfo.text = "Free: ${formatBytes(free)} / ${formatBytes(total)}"
        }
    }

    private fun startSelectionMode() {
        if (actionMode != null) return
        fileAdapter.selectionMode = true

        actionMode = startSupportActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menuInflater.inflate(R.menu.menu_selection, menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val selected = fileAdapter.selectedPaths.toList()
                return when (item.itemId) {
                    R.id.action_copy -> { viewModel.copyToClipboard(selected); mode.finish(); true }
                    R.id.action_cut -> { viewModel.cutToClipboard(selected); mode.finish(); true }
                    R.id.action_delete -> { confirmDelete(selected) { mode.finish() }; true }
                    R.id.action_select_all -> { fileAdapter.selectAll(); mode.title = "${fileAdapter.selectedPaths.size} selected"; true }
                    R.id.action_rename -> { if (selected.size == 1) showRenameDialog(selected[0]); mode.finish(); true }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                fileAdapter.clearSelection()
                actionMode = null
            }
        })
    }

    private fun confirmDelete(paths: List<String>, onConfirmed: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${paths.size} item(s)?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> viewModel.delete(paths); onConfirmed() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(path: String) {
        val currentName = path.substringAfterLast("/")
        val input = EditText(this).apply { setText(currentName); selectAll() }
        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) viewModel.rename(path, newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this).apply { hint = "Folder name" }
        AlertDialog.Builder(this)
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.createFolder(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openFile(item: FileItem) {
        try {
            val uri = Uri.parse("file://${item.path}")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_paste -> { viewModel.paste(); true }
            R.id.action_refresh -> { viewModel.refresh(); true }
            R.id.action_go_data -> { viewModel.loadPath("/sdcard/Android/data"); true }
            R.id.action_go_obb -> { viewModel.loadPath("/sdcard/Android/obb"); true }
            R.id.action_go_home -> { viewModel.loadPath("/sdcard"); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (fileAdapter.selectionMode) { actionMode?.finish(); return }
        if (!viewModel.navigateUp()) super.onBackPressed()
    }

    private fun showSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("One-Time Setup Required")
            .setMessage(
                "Kamal File Manager needs Device Owner access.\n\n" +
                "Run once via ADB:\n\n" +
                "adb shell dpm set-device-owner com.kovak.kamal/.DhizukuAdmin\n\n" +
                "Then restart the app."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showError(msg: String) {
        tvEmpty.text = msg
        tvEmpty.visibility = View.VISIBLE
        Snackbar.make(rvFiles, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}
