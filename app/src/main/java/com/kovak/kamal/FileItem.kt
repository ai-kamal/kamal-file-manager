package com.kovak.kamal

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FileItem(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val isHidden: Boolean,
    val lastModified: Long,
    val extension: String,
    val mimeType: String
) : Parcelable {

    val displaySize: String
        get() = when {
            isDirectory -> ""
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)} KB"
            size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024))} MB"
            else -> "${"%.1f".format(size / (1024.0 * 1024 * 1024))} GB"
        }

    val fileTypeIcon: FileType
        get() = when {
            isDirectory -> FileType.FOLDER
            extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> FileType.IMAGE
            extension in listOf("mp4", "mkv", "avi", "mov", "3gp", "webm") -> FileType.VIDEO
            extension in listOf("mp3", "wav", "aac", "flac", "ogg", "m4a") -> FileType.AUDIO
            extension in listOf("pdf") -> FileType.PDF
            extension in listOf("zip", "rar", "tar", "gz", "7z") -> FileType.ARCHIVE
            extension in listOf("apk") -> FileType.APK
            extension in listOf("txt", "log", "md", "xml", "json", "csv") -> FileType.TEXT
            extension in listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx") -> FileType.DOCUMENT
            else -> FileType.UNKNOWN
        }
}

enum class FileType {
    FOLDER, IMAGE, VIDEO, AUDIO, PDF, ARCHIVE, APK, TEXT, DOCUMENT, UNKNOWN
}
