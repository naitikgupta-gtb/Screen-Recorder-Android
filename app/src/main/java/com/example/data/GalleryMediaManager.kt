package com.example.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val width: Int,
    val height: Int,
    val path: String? = null
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format(Locale.getDefault(), "%.1f MB", mb)
            } else {
                val kb = sizeBytes / 1024.0
                String.format(Locale.getDefault(), "%.1f KB", kb)
            }
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM d, yyyy · hh:mm a", Locale.getDefault())
            return sdf.format(Date(dateAddedSec * 1000))
        }
}

data class VideoMediaTarget(
    val uri: Uri?,
    val filePath: String?,
    val pfd: ParcelFileDescriptor?
)

object GalleryMediaManager {
    private const val TAG = "GalleryMediaManager"
    const val ALBUM_DIRECTORY = "Movies/ScreenRecorder"

    /**
     * Creates a guaranteed clean local file and ParcelFileDescriptor in app cacheDir for raw MediaRecorder writing.
     * Passing a POSIX file descriptor across Binder IPC completely bypasses permission and SELinux
     * path limitations in native mediaserver across all Android versions (Android 7 - Android 15+).
     */
    fun createTempRecordingTarget(context: Context): Pair<File, ParcelFileDescriptor>? {
        return try {
            val cacheDir = context.cacheDir
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val tempFile = File(cacheDir, "rec_in_progress_${System.currentTimeMillis()}.mp4")
            if (tempFile.exists()) {
                tempFile.delete()
            }
            tempFile.createNewFile()
            val pfd = ParcelFileDescriptor.open(
                tempFile,
                ParcelFileDescriptor.MODE_READ_WRITE
            )
            Pair(tempFile, pfd)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create temp recording target in cacheDir", e)
            null
        }
    }

    /**
     * Saves a completed video (after MediaRecorder has stopped and written the MP4 moov atom).
     * ONLY saves if file size is > 1024 bytes, preventing any 0-byte ghost files!
     */
    fun saveCompletedVideo(context: Context, sourceFile: File): Uri? {
        if (!sourceFile.exists() || sourceFile.length() < 1024) {
            Log.e(TAG, "Cannot save video: source file is empty or missing (${sourceFile.length()} bytes)")
            return null
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ScreenRecord_$timeStamp.mp4"
        val resolver = context.contentResolver
        var finalUri: Uri? = null

        // 1. Android 10+ (API 29+): MediaStore Scoped Storage stream copy
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.TITLE, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenRecorder/")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri, "w")?.use { outStream ->
                        FileInputStream(sourceFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }

                    // Finalize in MediaStore
                    val updateValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, updateValues, null, null)

                    Log.d(TAG, "Successfully saved video to MediaStore: $uri (${sourceFile.length()} bytes)")
                    finalUri = uri
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore stream save failed, falling back to direct public file", e)
            }
        }

        // 2. Direct public file copy (Android 7-9 or fallback / legacy storage on Android 10)
        try {
            @Suppress("DEPRECATION")
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val screenRecorderDir = File(moviesDir, "ScreenRecorder").takeIf { it.exists() || it.mkdirs() }
                ?: File(moviesDir, "screenRecorder0").takeIf { it.exists() || it.mkdirs() }
                ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "ScreenRecorder").takeIf { it.exists() || it.mkdirs() }
                ?: File(context.filesDir, "ScreenRecorder").apply { mkdirs() }

            val destinationFile = File(screenRecorderDir, fileName)
            copyFile(sourceFile, destinationFile)

            // Register with system media scanner so it instantly shows in Phone Gallery & MediaStore
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destinationFile.absolutePath),
                arrayOf("video/mp4")
            ) { path, scannedUri ->
                Log.d(TAG, "MediaScanner registered file: $path -> $scannedUri")
            }

            if (finalUri == null) {
                // Insert direct MediaStore row if Scoped Storage didn't already
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.TITLE, fileName)
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.DATA, destinationFile.absolutePath)
                        put(MediaStore.Video.Media.SIZE, destinationFile.length())
                        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                        put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                    }
                    finalUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                } catch (_: Exception) {}

                if (finalUri == null) {
                    finalUri = Uri.fromFile(destinationFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy video to public directory", e)
        }

        return finalUri
    }

    /**
     * Cleans up any 0-byte ghost mp4 files left from previous failed attempts.
     */
    fun cleanupEmptyRecordings(context: Context) {
        try {
            // Cleanup MediaStore 0-byte items
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DISPLAY_NAME)
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Video.Media.SIZE} <= 0",
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: ""
                    if (name.contains("ScreenRecord", ignoreCase = true)) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                    }
                }
            }

            // Cleanup disk directories
            val dirs = mutableListOf<File>()
            try {
                @Suppress("DEPRECATION")
                dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenRecorder"))
                @Suppress("DEPRECATION")
                dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "ScreenRecorder"))
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { dirs.add(it) }
            } catch (_: Exception) {}

            for (dir in dirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.filter { it.extension.equals("mp4", ignoreCase = true) && it.length() == 0L }?.forEach { emptyFile ->
                        try {
                            emptyFile.delete()
                            Log.d(TAG, "Deleted 0-byte ghost file: ${emptyFile.absolutePath}")
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup empty recordings error: ${e.message}")
        }
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { inStream ->
            FileOutputStream(dst).use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    private fun getPathFromUri(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        return try {
            val projection = arrayOf(MediaStore.Video.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Query all videos saved under DCIM/ScreenRecorder, Movies/ScreenRecorder, or internal files.
     * Works seamlessly on both scoped storage (API 29+) and legacy file system (API 24-28).
     */
    fun queryGalleryRecordings(context: Context): List<RecordingItem> {
        cleanupEmptyRecordings(context)

        val recordings = mutableListOf<RecordingItem>()
        val resolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATA
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Recording_$id.mp4"
                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val size = cursor.getLong(sizeCol)

                    if (size > 0 && (
                        name.contains("ScreenRecord", ignoreCase = true) ||
                        name.contains("Screen_Record", ignoreCase = true) ||
                        (path != null && path.contains("ScreenRecorder", ignoreCase = true))
                    )) {
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        val duration = cursor.getLong(durationCol)
                        val size = cursor.getLong(sizeCol)
                        val date = cursor.getLong(dateCol)
                        val width = cursor.getInt(widthCol)
                        val height = cursor.getInt(heightCol)

                        recordings.add(
                            RecordingItem(
                                id = id,
                                uri = uri,
                                name = name,
                                durationMs = duration,
                                sizeBytes = size,
                                dateAddedSec = date,
                                width = width,
                                height = height,
                                path = path
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query recordings from MediaStore", e)
        }

        // Also check physical directories to never miss a file
        val candidateDirs = mutableListOf<File>()
        try {
            @Suppress("DEPRECATION")
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            candidateDirs.add(File(moviesDir, "ScreenRecorder"))
            candidateDirs.add(File(moviesDir, "screenRecorder0"))
            candidateDirs.add(moviesDir)
            @Suppress("DEPRECATION")
            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            candidateDirs.add(File(dcimDir, "ScreenRecorder"))
            candidateDirs.add(File(dcimDir, "Screenshots"))
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let {
                candidateDirs.add(File(it, "ScreenRecorder"))
                candidateDirs.add(it)
            }
            candidateDirs.add(File(context.filesDir, "ScreenRecorder"))
            candidateDirs.add(context.filesDir)
        } catch (_: Exception) {}

        for (dir in candidateDirs) {
            try {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.filter { it.extension.equals("mp4", ignoreCase = true) && it.length() > 0 }?.forEach { file ->
                        val alreadyInList = recordings.any { it.name == file.name || it.path == file.absolutePath }
                        if (!alreadyInList) {
                            recordings.add(
                                RecordingItem(
                                    id = file.lastModified(),
                                    uri = Uri.fromFile(file),
                                    name = file.name,
                                    durationMs = 0L,
                                    sizeBytes = file.length(),
                                    dateAddedSec = file.lastModified() / 1000,
                                    width = 720,
                                    height = 1280,
                                    path = file.absolutePath
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking candidate directory: ${dir.absolutePath}", e)
            }
        }

        return recordings.sortedByDescending { it.dateAddedSec }
    }

    fun shareVideo(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Recording").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun openInSystemGallery(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            shareVideo(context, uri)
        }
    }

    fun deleteRecording(context: Context, uri: Uri): Boolean {
        return try {
            if (uri.scheme == "file") {
                val file = uri.path?.let { File(it) }
                file?.delete() == true
            } else {
                val count = context.contentResolver.delete(uri, null, null)
                count > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting video uri: $uri", e)
            false
        }
    }
}
