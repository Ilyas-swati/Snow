package com.example.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

data class FileOpResult(
    val isSuccess: Boolean,
    val message: String,
    val targetPath: String? = null,
    val uri: Uri? = null
)

class FileManagerHelper(private val context: Context) {

    /**
     * Creates a folder in Downloads, Documents, or app-specific storage.
     * Verifies that the folder actually exists on disk after creation.
     */
    fun createFolder(parentLocation: String = "Downloads", folderName: String = "Snow"): FileOpResult {
        val safeName = folderName.trim().ifBlank { "Snow" }
        val targetBase = when (parentLocation.lowercase()) {
            "downloads", "download" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            "documents", "document" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            else -> context.getExternalFilesDir(null) ?: context.filesDir
        }

        // If public directory cannot be written due to scoped storage, use external files dir
        val baseDir = if (targetBase.canWrite()) {
            targetBase
        } else {
            File(context.getExternalFilesDir(null) ?: context.filesDir, parentLocation)
        }

        val newDir = File(baseDir, safeName)
        if (!newDir.exists()) {
            val created = newDir.mkdirs()
            if (!created && !newDir.exists()) {
                // Fallback inside external files dir
                val fallbackBase = context.getExternalFilesDir(null) ?: context.filesDir
                val fallbackDir = File(fallbackBase, safeName)
                fallbackDir.mkdirs()
                if (fallbackDir.exists() && fallbackDir.isDirectory) {
                    return FileOpResult(
                        isSuccess = true,
                        message = "Verified folder '$safeName' created successfully in app storage: ${fallbackDir.absolutePath}",
                        targetPath = fallbackDir.absolutePath
                    )
                }
                return FileOpResult(false, "Could not create folder '$safeName'. Check storage permissions.")
            }
        }

        // Verification check
        val verified = newDir.exists() && newDir.isDirectory
        return if (verified) {
            FileOpResult(
                isSuccess = true,
                message = "Verified folder '$safeName' created successfully at ${newDir.absolutePath}",
                targetPath = newDir.absolutePath
            )
        } else {
            FileOpResult(false, "Folder creation failed verification.")
        }
    }

    /**
     * Creates a file with content inside a specified folder, and verifies its existence.
     */
    fun createFile(folderName: String = "Snow", fileName: String, content: String = ""): FileOpResult {
        val safeFileName = fileName.trim().ifBlank { "test.txt" }
        val folderResult = createFolder("Downloads", folderName)
        val folderPath = folderResult.targetPath ?: (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath
        val targetFolder = File(folderPath)

        val targetFile = File(targetFolder, safeFileName)
        try {
            targetFile.writeText(content, Charsets.UTF_8)
            // Verification check
            if (targetFile.exists() && targetFile.length() >= 0) {
                return FileOpResult(
                    isSuccess = true,
                    message = "Verified file '$safeFileName' created successfully in '$folderName' (${targetFile.length()} bytes).",
                    targetPath = targetFile.absolutePath
                )
            }
            return FileOpResult(false, "File creation failed verification.")
        } catch (e: Exception) {
            Log.e("FileManagerHelper", "Error writing file $safeFileName", e)
            return FileOpResult(false, "Error writing file '$safeFileName': ${e.message}")
        }
    }

    /**
     * Opens folder in system File Manager.
     */
    fun openFolder(folderName: String = "Snow"): FileOpResult {
        try {
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folderName)
            val dir = if (downloadDir.exists()) downloadDir else File(context.getExternalFilesDir(null) ?: context.filesDir, folderName)

            val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            return FileOpResult(true, "Opened file manager for '$folderName'.", dir.absolutePath)
        } catch (e: Exception) {
            // Fallback generic file intent
            return try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                FileOpResult(true, "Opened system file browser.")
            } catch (ex: Exception) {
                FileOpResult(false, "Could not open system file manager: ${ex.message}")
            }
        }
    }

    /**
     * Shares a file via Android Share Sheet.
     */
    fun shareFile(fileName: String): FileOpResult {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val target = File(baseDir, fileName)
        if (!target.exists()) {
            // check downloads
            val dlTarget = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (!dlTarget.exists()) {
                return FileOpResult(false, "File '$fileName' does not exist to share.")
            }
        }
        val fileToShare = if (target.exists()) target else File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileToShare
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share $fileName").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            FileOpResult(true, "Opened share dialog for '${fileToShare.name}'.", fileToShare.absolutePath, contentUri)
        } catch (e: Exception) {
            Log.e("FileManagerHelper", "Error sharing file", e)
            FileOpResult(false, "Failed to share file: ${e.message}")
        }
    }
}
