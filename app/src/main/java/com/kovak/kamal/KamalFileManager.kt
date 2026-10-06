package com.kovak.kamal

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import com.rosan.dhizuku.api.DhizukuUserServiceArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class KamalFileManager(private val context: Context) {

    private var service: IRemoteService? = null
    private val gson = Gson()

    suspend fun init(): InitResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val ready = Dhizuku.init(context)
            if (!ready) return@withContext InitResult.DHIZUKU_NOT_AVAILABLE

            if (!Dhizuku.isPermissionGranted()) {
                val granted = suspendCancellableCoroutine { cont ->
                    Dhizuku.requestPermission(object : DhizukuRequestPermissionListener {
                        override fun onRequestPermission(grantResult: Int) {
                            cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                        }
                    })
                }
                if (!granted) return@withContext InitResult.PERMISSION_DENIED
            }

            val args = DhizukuUserServiceArgs(
                ComponentName(context, RemoteService::class.java)
            )

            val connected = suspendCancellableCoroutine { cont ->
                Dhizuku.bindUserService(args, object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        service = IRemoteService.Stub.asInterface(binder)
                        cont.resume(true)
                    }
                    override fun onServiceDisconnected(name: ComponentName) {
                        service = null
                    }
                })
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
