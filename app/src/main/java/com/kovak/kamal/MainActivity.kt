package com.kovak.kamal

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    // ── Views ─────────────────────────────────────────────────────
    private lateinit var toolbar: Toolbar
    private lateinit var rvFiles: RecyclerView
    private lateinit var rvBreadcrumb: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var fabNewFolder: FloatingActionButton
    private lateinit var tvStorageInfo: TextView
    private lateinit var btnToggleView: ImageButton
    private lateinit var btnSort: ImageButton
    private lateinit var searchView: SearchView

    // Bottom nav
    private lateinit var navHome: LinearLayout
    private lateinit var navDownloads: LinearLayout
    private lateinit var navImages: LinearLayout
    private lateinit var navVideos: LinearLayout
    private lateinit var navAppData: LinearLayout

    // ── Adapters / VM ─────────────────────────────────────────────
    private lateinit var fileAdapter: FileAdapter
    private lateinit var breadcrumbAdapter: BreadcrumbAdapter
    private lateinit var viewModel: FileViewModel
    private lateinit var manager: KamalFileManager

    private var actionMode: ActionMode? = null
    private var isGridMode = false

    // ── Lifecycle ─────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        manager = KamalFileManager(this)
        setupRecyclerViews()
        setupFab()
        setupToolbarButtons()
        setupBottomNav()
        initManager()
    }

    // ── View binding ──────────────────────────────────────────────
    private fun bindViews() {
        toolbar         = findViewById(R.id.toolbar)
        rvFiles         = findViewById(R.id.rvFiles)
        rvBreadcrumb    = findViewById(R.id.rvBreadcrumb)
        progressBar     = findViewById(R.id.progressBar)
        layoutEmpty     = findViewById(R.id.layoutEmpty)
        tvEmpty         = findViewById(R.id.tvEmpty)
        fabNewFolder    = findViewById(R.id.fabNewFolder)
        tvStorageInfo   = findViewById(R.id.tvStorageInfo)
        btnToggleView   = findViewById(R.id.btnToggleView)
        btnSort         = findViewById(R.id.btnSort)
        searchView      = findViewById(R.id.searchView)
        navHome         = findViewById(R.id.navHome)
        navDownloads    = findViewById(R.id.navDownloads)
        navImages       = findViewById(R.id.navImages)
        navVideos       = findViewById(R.id.navVideos)
        navAppData      = findViewById(R.id.navAppData)
    }

    // ── Init manager ──────────────────────────────────────────────
    private fun initManager() {
        progressBar.visibility = View.VISIBLE
        layoutEmpty.visibility = View.VISIBLE
        tvEmpty.text = "Connecting to Kamal File Manager…"

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
                    showError("Permission denied. Grant device owner via ADB.")
                }
                else -> {
                    progressBar.visibility = View.GONE
                    showError("Initialization failed. Run setup command and restart.")
                }
            }
        }
    }

    // ── Observers ─────────────────────────────────────────────────
    private fun observeViewModel() {
        viewModel.files.observe(this) { files ->
            fileAdapter.submitList(files)
            val empty = files.isEmpty()
            layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            if (empty) tvEmpty.text = getString(R.string.empty_folder)
        }

        viewModel.loading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.currentPath.observe(this) { updateBreadcrumbs() }

        viewModel.toastMsg.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }

        viewModel.error.observe(this) { err ->
            err?.let {
                tvEmpty.text = it
                layoutEmpty.visibility = View.VISIBLE
            }
        }
    }

    // ── RecyclerViews ─────────────────────────────────────────────
    private fun setupRecyclerViews() {
        fileAdapter = FileAdapter(
            context = this,
            onItemClick = { item ->
                if (item.isDirectory) viewModel.loadPath(item.path)
                else openFile(item)
            },
            onItemLongClick = { item, _ ->
                if (!fileAdapter.selectionMode) startSelectionMode()
                fileAdapter.toggleSelection(item.path)
                true
            },
            onSelectionChanged = { count ->
                actionMode?.title = "$count selected"
            }
        )

        rvFiles.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = fileAdapter
        }

        breadcrumbAdapter = BreadcrumbAdapter { path -> viewModel.loadPath(path) }
        rvBreadcrumb.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = breadcrumbAdapter
        }
    }

    // ── FAB ───────────────────────────────────────────────────────
    private fun setupFab() {
        fabNewFolder.setOnClickListener { showCreateFolderDialog() }
    }

    // ── Toolbar buttons ────────────────────────────────────────────
    private fun setupToolbarButtons() {
        // Grid / List toggle
        btnToggleView.setOnClickListener {
            isGridMode = !isGridMode
            fileAdapter.isGridMode = isGridMode
            rvFiles.layoutManager = if (isGridMode)
                GridLayoutManager(this, 3)
            else
                LinearLayoutManager(this)
            btnToggleView.setImageResource(
                if (isGridMode) R.drawable.ic_list_view else R.drawable.ic_grid
            )
        }

        // Sort popup
        btnSort.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.apply {
                add(0, 0, 0, getString(R.string.sort_name_az))
                add(0, 1, 1, getString(R.string.sort_name_za))
                add(0, 2, 2, getString(R.string.sort_size_asc))
                add(0, 3, 3, getString(R.string.sort_size_desc))
                add(0, 4, 4, getString(R.string.sort_date_new))
                add(0, 5, 5, getString(R.string.sort_date_old))
                add(0, 6, 6, getString(R.string.sort_type))
            }
            popup.setOnMenuItemClickListener { menuItem ->
                if (!::viewModel.isInitialized) return@setOnMenuItemClickListener false
                val mode = when (menuItem.itemId) {
                    0 -> FileViewModel.SortMode.NAME_ASC
                    1 -> FileViewModel.SortMode.NAME_DESC
                    2 -> FileViewModel.SortMode.SIZE_ASC
                    3 -> FileViewModel.SortMode.SIZE_DESC
                    4 -> FileViewModel.SortMode.DATE_DESC
                    5 -> FileViewModel.SortMode.DATE_ASC
                    6 -> FileViewModel.SortMode.TYPE
                    else -> FileViewModel.SortMode.NAME_ASC
                }
                viewModel.setSort(mode)
                true
            }
            popup.show()
        }

        // Search
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                if (::viewModel.isInitialized) viewModel.setSearch(newText ?: "")
                return true
            }
        })
    }

    // ── Bottom nav ────────────────────────────────────────────────
    private fun setupBottomNav() {
        navHome.setOnClickListener      { navigateTo("/sdcard") }
        navDownloads.setOnClickListener { navigateTo("/sdcard/Download") }
        navImages.setOnClickListener    { navigateTo("/sdcard/DCIM") }
        navVideos.setOnClickListener    { navigateTo("/sdcard/Movies") }
        navAppData.setOnClickListener   { navigateTo("/sdcard/Android/data") }
    }

    private fun navigateTo(path: String) {
        if (!::viewModel.isInitialized) return
        viewModel.loadPath(path)
    }

    // ── Breadcrumbs ────────────────────────────────────────────────
    private fun updateBreadcrumbs() {
        val crumbs = viewModel.breadcrumbs
        breadcrumbAdapter.submitList(crumbs)
        rvBreadcrumb.scrollToPosition(crumbs.size - 1)
    }

    // ── Storage info ───────────────────────────────────────────────
    private fun loadStorageInfo() {
        lifecycleScope.launch {
            val free  = manager.getFreeSpace("/sdcard")
            val total = manager.getTotalSpace("/sdcard")
            tvStorageInfo.text = "Free ${formatBytes(free)} of ${formatBytes(total)}"
        }
    }

    // ── Selection mode ────────────────────────────────────────────
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
                    R.id.action_copy       -> { viewModel.copyToClipboard(selected); mode.finish(); true }
                    R.id.action_cut        -> { viewModel.cutToClipboard(selected); mode.finish(); true }
                    R.id.action_delete     -> { confirmDelete(selected) { mode.finish() }; true }
                    R.id.action_share      -> { shareFiles(selected); mode.finish(); true }
                    R.id.action_open_with  -> { if (selected.size == 1) openFileWith(selected[0]); mode.finish(); true }
                    R.id.action_select_all -> { fileAdapter.selectAll(); true }
                    R.id.action_rename     -> { if (selected.size == 1) showRenameDialog(selected[0]); mode.finish(); true }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                fileAdapter.clearSelection()
                actionMode = null
            }
        })
    }

    // ── File ops ──────────────────────────────────────────────────
    private fun openFile(item: FileItem) {
        try {
            val file = File(item.path)
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType.ifEmpty { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFileWith(path: String) {
        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val mimeType = contentResolver.getType(uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.action_open_with)))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        try {
            val uris = ArrayList(paths.map { path ->
                FileProvider.getUriForFile(this, "$packageName.provider", File(path))
            })
            val intent = if (uris.size == 1) {
                val mime = contentResolver.getType(uris[0]) ?: "*/*"
                Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────
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

    private fun showSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("One-Time Setup Required")
            .setMessage(
                "Kamal File Manager needs Device Owner access.\n\n" +
                "Run once via ADB:\n\n" +
                "adb shell dpm set-device-owner \\\n  com.kovak.kamal/.DhizukuAdmin\n\n" +
                "Then restart the app."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ── Menu ──────────────────────────────────────────────────────
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!::viewModel.isInitialized) return false
        return when (item.itemId) {
            R.id.action_paste    -> { viewModel.paste(); true }
            R.id.action_refresh  -> { viewModel.refresh(); true }
            R.id.action_go_data  -> { viewModel.loadPath("/sdcard/Android/data"); true }
            R.id.action_go_obb   -> { viewModel.loadPath("/sdcard/Android/obb"); true }
            R.id.action_go_home  -> { viewModel.loadPath("/sdcard"); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── Back nav ──────────────────────────────────────────────────
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (fileAdapter.selectionMode) { actionMode?.finish(); return }
        if (!::viewModel.isInitialized) { super.onBackPressed(); return }
        if (!viewModel.navigateUp()) super.onBackPressed()
    }

    // ── Error / util ──────────────────────────────────────────────
    private fun showError(msg: String) {
        tvEmpty.text = msg
        layoutEmpty.visibility = View.VISIBLE
        Snackbar.make(rvFiles, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L               -> "$bytes B"
        bytes < 1024L * 1024        -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else                        -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}
