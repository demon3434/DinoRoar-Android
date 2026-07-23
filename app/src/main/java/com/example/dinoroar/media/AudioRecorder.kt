package com.example.dinoroar.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "AudioRecorder"
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isRecording = false

    fun startRecording(): File? {
        if (isRecording) {
            Log.w(TAG, "Already recording. Stop first.")
            return null
        }

        val cacheDir = context.cacheDir
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val audioFile = File(cacheDir, "REC_$timeStamp.m4a")
        currentOutputFile = audioFile

        var recorder: MediaRecorder? = null
        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                try {
                    setAudioSamplingRate(16000)
                    setAudioEncodingBitRate(64000)
                } catch (e: Exception) {
                    Log.w(TAG, "Custom sampling rate not supported, using default: ${e.message}")
                }
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
        } catch (e: Throwable) {
            Log.e(TAG, "MediaRecorder start failed: ${e.message}", e)
            try {
                recorder?.reset()
                recorder?.release()
            } catch (_: Exception) {}
            mediaRecorder = null
            currentOutputFile = null
            isRecording = false
        }

        return currentOutputFile
    }

    fun stopRecording(): File? {
        if (!isRecording) return null

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder: ${e.message}")
        } finally {
            release()
        }

        val resultFile = currentOutputFile
        currentOutputFile = null
        return resultFile
    }

    private fun release() {
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
    }

    fun cancelRecording() {
        if (isRecording) {
            stopRecording()
        }
        currentOutputFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        currentOutputFile = null
    }

    fun isRecording(): Boolean = isRecording
}
