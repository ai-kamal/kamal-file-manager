package com.kovak.kamal

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

class KamalFileManager(private val context: Context) {

    private var service: IRemoteService? = null
    private var serviceConnection: ServiceConnection? = null
    private val gson = Gson()

    suspend fun init(): InitResult {
        return try {
            // Is Shizuku running?
            if (!Shizuku.pingBinder()) {
                return InitResult.DHIZUKU_NOT_AVAILABLE
            }

            // Request permission if not already granted
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                val granted = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        val listener = object : Shizuku.OnRequestPermissionResultListener {
                            override fun onRequestPermissionResult(reqCode: Int, grantResult: Int) {
                                Shizuku.removeRequestPermissionResultListener(this)
                                if (cont.isActive)
                                    cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                            }
                        }
                        Shizuku.addRequestPermissionResultListener(listener)
                        cont.invokeOnCancellation {
                            Shizuku.removeRequestPermissionResultListener(listener)
                        }
                        Shizuku.requestPermission(42)
                    }
                }
                if (!granted) return InitResult.PERMISSION_DENIED
            }

            // Bind RemoteService via Shizuku UserService
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, RemoteService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("service")
                .debuggable(false)
                .version(1)

            val connected = suspendCancellableCoroutine<Boolean> { cont ->
                serviceConnection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        service = IRemoteService.Stub.asInterface(binder)
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onServiceDisconnected(name: ComponentName) {
                        service = null
                    }
                }
                try {
                    Shizuku.bindUserService(args, serviceConnection!!)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(false)
                }
            }

            if (connected) InitResult.SUCCESS else InitResult.SERVICE_FAILED
        } catch (e: Exception) {
            InitResult.ERROR
        }
    }

    fun isReady() = service != null

    suspend fun listFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            val json = service?.listFiles(path) ?: return@withContext emptyList()
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val raw: List<Map<String, Any>> = gson.fromJson(json, type)
            raw.map { map ->
                FileItem(
                    name = map["name"] as? String ?: "",
                    path = map["path"] as? String ?: "",
                    size = (map["size"] as? Double)?.toLong() ?: 0L,
                    isDirectory = map["isDirectory"] as? Boolean ?: false,
                    isHidden = map["isHidden"] as? Boolean ?: false,
                    lastModified = (map["lastModified"] as? Double)?.toLong() ?: 0L,
                    extension = map["extension"] as? String ?: "",
                    mimeType = map["mimeType"] as? String ?: ""
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun readFile(path: String): ByteArray? = withContext(Dispatchers.IO) {
        try { service?.readFile(path) } catch (e: Exception) { null }
    }

    suspend fun writeFile(path: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try { service?.writeFile(path, data) ?: false } catch (e: Exception) { false }
    }

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        try { service?.deleteFile(path) ?: false } catch (e: Exception) { false }
    }

    suspend fun copy(src: String, dest: String): Boolean = withContext(Dispatchers.IO) {
        try { service?.copyFile(src, dest) ?: false } catch (e: Exception) { false }
    }

    suspend fun move(src: String, dest: String): Boolean = withContext(Dispatchers.IO) {
        try { service?.moveFile(src, dest) ?: false } catch (e: Exception) { false }
    }

    suspend fun createFolder(path: String): Boolean = withContext(Dispatchers.IO) {
        try { service?.createDirectory(path) ?: false } catch (e: Exception) { false }
    }

    suspend fun getFreeSpace(path: String): Long = withContext(Dispatchers.IO) {
        try { service?.getFreeSpace(path) ?: 0L } catch (e: Exception) { 0L }
    }

    suspend fun getTotalSpace(path: String): Long = withContext(Dispatchers.IO) {
        try { service?.getTotalSpace(path) ?: 0L } catch (e: Exception) { 0L }
    }

    enum class InitResult {
        SUCCESS, DHIZUKU_NOT_AVAILABLE, PERMISSION_DENIED, SERVICE_FAILED, ERROR
    }
}
