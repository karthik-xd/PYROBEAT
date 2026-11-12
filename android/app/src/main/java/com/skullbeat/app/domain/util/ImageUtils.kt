package com.skullbeat.app.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object ImageUtils {

    suspend fun processAndSavePlaylistCover(
        context: Context,
        sourceUri: Uri,
        playlistId: Long,
        oldCoverPath: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Decode bounds to compute inSampleSize
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return@withContext null

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return@withContext null

            // Target max dimension 512px
            val targetSize = 512
            var inSampleSize = 1
            val minDim = min(originalWidth, originalHeight)
            while ((minDim / inSampleSize) > targetSize * 2) {
                inSampleSize *= 2
            }

            // 2. Decode sampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return@withContext null

            // 3. Check EXIF orientation
            var rotationDegrees = 0
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    val exif = ExifInterface(input)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotationDegrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (_: Exception) {}

            // Rotate if needed
            val orientedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(
                    sampledBitmap, 0, 0,
                    sampledBitmap.width, sampledBitmap.height,
                    matrix, true
                ).also {
                    if (it != sampledBitmap) sampledBitmap.recycle()
                }
            } else {
                sampledBitmap
            }

            // 4. Center-crop to 1:1 square
            val squareDim = min(orientedBitmap.width, orientedBitmap.height)
            val cropX = (orientedBitmap.width - squareDim) / 2
            val cropY = (orientedBitmap.height - squareDim) / 2
            val croppedBitmap = Bitmap.createBitmap(
                orientedBitmap,
                cropX, cropY,
                squareDim, squareDim
            ).also {
                if (it != orientedBitmap) orientedBitmap.recycle()
            }

            // 5. Scale to 512x512
            val finalBitmap = if (croppedBitmap.width != targetSize || croppedBitmap.height != targetSize) {
                Bitmap.createScaledBitmap(croppedBitmap, targetSize, targetSize, true).also {
                    if (it != croppedBitmap) croppedBitmap.recycle()
                }
            } else {
                croppedBitmap
            }

            // 6. Save to internal filesDir/playlist_covers/pl_{playlistId}_{timestamp}.webp
            val coverDir = File(context.filesDir, "playlist_covers")
            if (!coverDir.exists()) coverDir.mkdirs()

            val newFile = File(coverDir, "pl_${playlistId}_${System.currentTimeMillis()}.webp")
            FileOutputStream(newFile).use { fos ->
                val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                finalBitmap.compress(format, 85, fos)
            }
            finalBitmap.recycle()

            // 7. Delete old cover file if it exists and differs
            deleteCoverFile(oldCoverPath)

            newFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteCoverFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    fun createTempCameraUri(context: Context): Pair<Uri, File>? {
        return try {
            val cacheDir = File(context.cacheDir, "camera_temp")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val tempFile = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            Pair(uri, tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
