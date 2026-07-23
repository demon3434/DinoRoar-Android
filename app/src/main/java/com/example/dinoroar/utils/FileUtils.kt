package com.example.dinoroar.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.roundToInt

object FileUtils {
    private const val TAG = "FileUtils"

    fun compressImage(context: Context, inputUri: Uri): File? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(inputUri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Target size limits: 1920 max edge
            val maxEdge = 1920f
            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            var inSampleSize = 1

            if (srcWidth > maxEdge || srcHeight > maxEdge) {
                val ratio = if (srcWidth > srcHeight) srcWidth / maxEdge else srcHeight / maxEdge
                inSampleSize = ratio.roundToInt()
            }

            // Decode with inSampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            inputStream = context.contentResolver.openInputStream(inputUri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()

            if (bitmap == null) return null

            // Write compressed file to cache
            val cacheDir = context.cacheDir
            val compressedFile = File(cacheDir, "IMG_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(compressedFile)

            // Compress until size is less than 1MB (min quality 60, step 5)
            var quality = 90
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.flush()
            outputStream.close()

            var fileSize = compressedFile.length()
            while (fileSize > 1024 * 1024 && quality > 60) {
                quality -= 5
                val os = FileOutputStream(compressedFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, os)
                os.flush()
                os.close()
                fileSize = compressedFile.length()
            }

            return compressedFile

        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress image", e)
            return null
        } finally {
            inputStream?.close()
        }
    }

    fun calculateMd5(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val md5Bytes = digest.digest()
            md5Bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate MD5 of file", e)
            null
        }
    }

    fun calculateMd5(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val md5Bytes = digest.digest()
            md5Bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate MD5 of Uri", e)
            null
        }
    }
}
