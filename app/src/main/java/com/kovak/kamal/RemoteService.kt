package com.kovak.kamal

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.webkit.MimeTypeMap

/**
 * RemoteService — runs as device owner process via Dhizuku IPC.
 * Has elevated permissions to read/write /sdcard/Android/data/ freely.
 */
class RemoteService : IRemoteService.Stub() {

    private val gson = Gson()

    override fun listFiles(path: String): String {
        return try {
            val dir = File(path)
            if (!dir.exists() || !dir.canRead()) {
                return gson.toJson(emptyList<Any>())
            }

            val files = dir.listFiles() ?: return gson.toJson(emptyList<Any>())

            val list = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map { f ->
                    mapOf(
                        "name" to f.name,
                        "path" to f.absolutePath,
                        "size" to if (f.isFile) f.length() else 0L,
                        "isDirectory" to f.isDirectory,
                        "isHidden" to f.isHidden,
                        "lastModified" to f.lastModified(),
                        "extension" to (f.extension.lowercase()),
                        "mimeType" to getMimeType(f)
                    )
                }

            gson.toJson(list)
        } catch (e: Exception) {
            gson.toJson(emptyList<Any>())
        }
    }

    override fun readFile(path: String): ByteArray? {
        return try {
            File(path).readBytes()
        } catch (e: Exception) {
            null
        }
    }

    override fun writeFile(path: String, data: ByteArray): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { it.write(data) }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun deleteFile(path: String): Boolean {
        return try {
            File(path).deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    override fun copyFile(src: String, dest: String): Boolean {
        return try {
            val source = File(src)
            val destination = File(dest)
            destination.parentFile?.mkdirs()
            if (source.isDirectory) {
                source.copyRecursively(destination, overwrite = true)
            } else {
                source.copyTo(destination, overwrite = true)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun moveFile(src: String, dest: String): Boolean {
        return try {
            val source = File(src)
            val destination = File(dest)
            destination.parentFile?.mkdirs()
            source.renameTo(destination)
        } catch (e: Exception) {
            false
        }
    }

    override fun createDirectory(path: String): Boolean {
        return try {
            File(path).mkdirs()
        } catch (e: Exception) {
            false
        }
    }

    override fun getFileInfo(path: String): String {
        return try {
            val f = File(path)
            val obj = JsonObject().apply {
                addProperty("name", f.name)
                addProperty("path", f.absolutePath)
                addProperty("size", if (f.isFile) f.length() else 0L)
                addProperty("isDirectory", f.isDirectory)
                addProperty("isHidden", f.isHidden)
                addProperty("lastModified", f.lastModified())
                addProperty("extension", f.extension.lowercase())
                addProperty("mimeType", getMimeType(f))
                addProperty("canRead", f.canRead())
                addProperty("canWrite", f.canWrite())
                addProperty("exists", f.exists())
            }
            obj.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    override fun exists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: Exception) {
            false
        }
    }

    override fun getFreeSpace(path: String): Long {
        return try {
            File(path).freeSpace
        } catch (e: Exception) {
            0L
        }
    }

    override fun getTotalSpace(path: String): Long {
        return try {
            File(path).totalSpace
        } catch (e: Exception) {
            0L
        }
    }

    private fun getMimeType(file: File): String {
        if (file.isDirectory) return "inode/directory"
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
