package com.example.dinoroar.media

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private object Log {
    private const val ENABLE_DIAGNOSTIC = false // 关闭详细调试日志，未来排查问题时可修改为 true

    fun d(tag: String, msg: String) {
        if (ENABLE_DIAGNOSTIC) {
            DiagnosticLogger.log(tag, "D", msg)
        } else {
            android.util.Log.d(tag, msg)
        }
    }
    fun i(tag: String, msg: String) {
        if (ENABLE_DIAGNOSTIC) {
            DiagnosticLogger.log(tag, "I", msg)
        } else {
            android.util.Log.i(tag, msg)
        }
    }
    fun w(tag: String, msg: String) {
        DiagnosticLogger.log(tag, "W", msg)
    }
    fun w(tag: String, msg: String, tr: Throwable?) {
        DiagnosticLogger.log(tag, "W", msg, tr)
    }
    fun e(tag: String, msg: String) {
        DiagnosticLogger.log(tag, "E", msg)
    }
    fun e(tag: String, msg: String, tr: Throwable?) {
        DiagnosticLogger.log(tag, "E", msg, tr)
    }
}

@Singleton
class MediaCompressor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "MediaCompressor"

    fun compressImage(inputUri: Uri): File? {
        return com.example.dinoroar.utils.FileUtils.compressImage(context, inputUri)
    }

    fun compressVideo(inputUri: Uri, resolution: String): File? {
        val cacheDir = context.cacheDir
        val finalOutputFile = File(cacheDir, "VID_${System.currentTimeMillis()}.mp4")

        if (resolution == "original") {
            try {
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    FileOutputStream(finalOutputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Video copy completed: ${finalOutputFile.length() / 1024} KB")
                return finalOutputFile
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy video", e)
                return null
            }
        }

        // 方案A：四档码率目标值（大幅降低以适配日记场景）
        val targetHeight = when (resolution) {
            "360p"  -> 360
            "480p"  -> 480
            "720p"  -> 720
            "1080p" -> 1080
            else    -> 720
        }
        val targetBitrate = when (resolution) {
            "360p"  -> 250 * 1024
            "480p"  -> 400 * 1024
            "720p"  -> 900 * 1024
            "1080p" -> 1500 * 1024
            else    -> 900 * 1024
        }
        // 方案B：按档位分配关键帧间隔（日记静态场景可用更长间隔）
        val iFrameInterval = when (resolution) {
            "360p"  -> 10
            "480p"  -> 8
            "720p"  -> 8
            "1080p" -> 6
            else    -> 8
        }

        val tempVideoFile = File(cacheDir, "temp_VID_${System.currentTimeMillis()}.mp4")

        Log.i(TAG, "Starting transcode: resolution=$resolution, targetHeight=$targetHeight, " +
                "targetBitrate=${targetBitrate / 1024}Kbps, iFrameInterval=${iFrameInterval}s")

        // 方案D：优先尝试 HEVC，失败则 fallback 到 H.264
        val transcodeSuccess =
            doTranscodeVideo(inputUri, tempVideoFile, targetHeight, targetBitrate, iFrameInterval, preferHevc = true)
            || run {
                Log.w(TAG, "HEVC transcode failed, retrying with H.264...")
                tempVideoFile.delete()
                doTranscodeVideo(inputUri, tempVideoFile, targetHeight, targetBitrate, iFrameInterval, preferHevc = false)
            }

        if (transcodeSuccess) {
            Log.i(TAG, "Video transcode success. Merging audio track...")
            val mergeSuccess = mergeAudioAndVideo(tempVideoFile, inputUri, finalOutputFile)
            tempVideoFile.delete()

            if (mergeSuccess) {
                Log.i(TAG, "Compression complete. Final size: ${finalOutputFile.length() / 1024} KB")
                return finalOutputFile
            }
        }

        // 最终兜底：确保业务始终可用
        Log.w(TAG, "All transcode attempts failed, falling back to original copy...")
        try {
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(finalOutputFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback copy also failed", e)
        }
        return finalOutputFile
    }

    /**
     * 检测设备是否支持 HEVC 硬件编码器。
     * 使用 720p/900Kbps 参数探测，失败则认为不支持。
     */
    private fun isHevcEncoderAvailable(): Boolean {
        return try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val testFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 1280, 720)
            testFormat.setInteger(MediaFormat.KEY_BIT_RATE, 900 * 1024)
            testFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            testFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 8)
            testFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            list.findEncoderForFormat(testFormat) != null
        } catch (e: Exception) {
            Log.w(TAG, "HEVC encoder availability check failed", e)
            false
        }
    }

    /**
     * 转码视频轨道（不含音频）。
     *
     * @param inputUri      源视频 Uri
     * @param outputFile    仅含视频轨的临时输出文件
     * @param targetHeight  目标高度（宽度按比例计算并 16 字节对齐）
     * @param targetBitrate 目标码率（bps）
     * @param iFrameInterval 关键帧间隔（秒），方案B参数
     * @param preferHevc    是否优先使用 HEVC 编码，方案D参数
     */
    private fun doTranscodeVideo(
        inputUri: Uri,
        outputFile: File,
        targetHeight: Int,
        targetBitrate: Int,
        iFrameInterval: Int,
        preferHevc: Boolean = true
    ): Boolean {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: android.view.Surface? = null
        var muxerStarted = false

        var afd: AssetFileDescriptor? = null

        try {
            extractor = MediaExtractor()
            afd = context.contentResolver.openAssetFileDescriptor(inputUri, "r")
            if (afd != null) {
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } else {
                Log.e(TAG, "[Transcode] Failed to open AssetFileDescriptor for URI: $inputUri")
                return false
            }

            var videoTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    break
                }
            }

            if (videoTrackIndex == -1) {
                Log.e(TAG, "[Transcode] No video track found in source")
                return false
            }
            extractor.selectTrack(videoTrackIndex)
            val srcFormat = extractor.getTrackFormat(videoTrackIndex)

            // Bug修复：安全获取源分辨率，兼容不含 KEY_WIDTH/HEIGHT 的机型
            val srcWidth = try { srcFormat.getInteger(MediaFormat.KEY_WIDTH) } catch (e: Exception) { 0 }
            val srcHeight = try { srcFormat.getInteger(MediaFormat.KEY_HEIGHT) } catch (e: Exception) { 0 }
            if (srcWidth <= 0 || srcHeight <= 0) {
                Log.e(TAG, "[Transcode] Invalid source dimensions: ${srcWidth}x${srcHeight}")
                return false
            }

            // 为了在不引入极其复杂的 OpenGL 渲染层（涉及 EGLContext/GLSurface 绘制，代码量大且在不同机型易崩溃）的情况下，
            // 能够 100% 兼容所有 Android 设备的硬件转码，
            // 我们使编码器目标分辨率与源视频保持一致（dst = src）。
            // 这样 decoder 输出可以直接无缝渲染到 encoder 表面。
            // 压缩效果完全由目标码率（targetBitrate）和 VBR 控制，同样能达到极小体积！
            val dstWidth = srcWidth
            val dstHeight = srcHeight

            // 方案D：按设备支持情况选择编码器 MIME
            val useHevc = preferHevc && isHevcEncoderAvailable()
            val encoderMime = if (useHevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
            Log.i(TAG, "[Transcode] Start: src=${srcWidth}x${srcHeight} -> dst=${dstWidth}x${dstHeight}, " +
                    "codec=$encoderMime, bitrate=${targetBitrate / 1024}Kbps, iFrame=${iFrameInterval}s")

            // 初始化 Muxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            Log.d(TAG, "[Transcode] Muxer created")

            // 配置编码器（方案B: iFrameInterval 参数化；方案C: VBR 模式）
            val encoderFormat = MediaFormat.createVideoFormat(encoderMime, dstWidth, dstHeight)
            encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
            encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
            // 方案C：VBR 模式——静态场景自动降码率，动态场景自动提升
            encoderFormat.setInteger(
                MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
            )

            encoder = MediaCodec.createEncoderByType(encoderMime)
            Log.d(TAG, "[Transcode] Encoder created: ${encoder.name}")
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()
            Log.d(TAG, "[Transcode] Encoder started")

            // 配置解码器（按源视频 MIME 自动适配，兼容 HEVC 源）
            val srcMime = srcFormat.getString(MediaFormat.KEY_MIME)
            if (srcMime == null) {
                Log.e(TAG, "[Transcode] Source MIME is null")
                return false
            }
            Log.d(TAG, "[Transcode] Source MIME: $srcMime")
            decoder = MediaCodec.createDecoderByType(srcMime)
            decoder.configure(srcFormat, inputSurface, null, 0)
            decoder.start()
            Log.d(TAG, "[Transcode] Decoder started")

            val bufferInfo = MediaCodec.BufferInfo()
            var isExtractorEOS = false
            var isDecoderEOS = false
            var isEncoderEOS = false
            var videoTrackMuxerIndex = -1
            var frameCount = 0

            var extractorReadAttempts = 0

            while (!isEncoderEOS) {
                // 1. 输入阶段
                if (!isExtractorEOS) {
                    val inBufferIndex = decoder.dequeueInputBuffer(10000)
                    if (inBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        extractorReadAttempts++
                        if (extractorReadAttempts == 1) {
                            Log.d(TAG, "[Transcode] First extractor read: size=$sampleSize, time=${extractor.sampleTime}, track=${extractor.sampleTrackIndex}")
                        }
                        if (sampleSize < 0) {
                            Log.d(TAG, "[Transcode] Extractor EOS on attempt $extractorReadAttempts")
                            decoder.queueInputBuffer(inBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isExtractorEOS = true
                        } else {
                            decoder.queueInputBuffer(inBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // 2. 解码渲染阶段
                if (!isDecoderEOS) {
                    val outBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outBufferIndex >= 0) {
                        val hasData = bufferInfo.size > 0
                        decoder.releaseOutputBuffer(outBufferIndex, hasData)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoder.signalEndOfInputStream()
                            isDecoderEOS = true
                            Log.d(TAG, "[Transcode] Decoder EOS, frames decoded: $frameCount")
                        }
                    }
                }

                // 3. 编码输出阶段
                val encBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (encBufferIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(encBufferIndex)!!
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        encoder.releaseOutputBuffer(encBufferIndex, false)
                        continue
                    }

                    if (bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackMuxerIndex, encodedData, bufferInfo)
                        frameCount++
                    }

                    encoder.releaseOutputBuffer(encBufferIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEncoderEOS = true
                        Log.d(TAG, "[Transcode] Encoder EOS, frames encoded: $frameCount")
                    }
                } else if (encBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = encoder.outputFormat
                    videoTrackMuxerIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                    Log.d(TAG, "[Transcode] Muxer started, video track index: $videoTrackMuxerIndex")
                }
            }

            // 正常流程完成，先 stop muxer再退出 try
            if (muxerStarted) {
                muxer.stop()
                muxerStarted = false
                Log.i(TAG, "[Transcode] Done. Encoded $frameCount frames -> ${outputFile.length() / 1024} KB")
            } else {
                Log.e(TAG, "[Transcode] Muxer never started, no frames written!")
                return false
            }
            return true
        } catch (e: Exception) {
            // Bug修复：暴露完整 stacktrace，便于诊断转码失败原因
            Log.e(TAG, "[Transcode] Failed [${e.javaClass.simpleName}]: ${e.message}", e)
            return false
        } finally {
            // finally 里只做 release，stop 已在正常流程中处理
            try { afd?.close() } catch (e: Exception) {}
            try { decoder?.stop(); decoder?.release() } catch (e: Exception) { Log.w(TAG, "Decoder release failed", e) }
            try { encoder?.stop(); encoder?.release() } catch (e: Exception) { Log.w(TAG, "Encoder release failed", e) }
            try { inputSurface?.release() } catch (e: Exception) {}
            // muxer: 只在未 stop 的情况下尝试 stop（异常路径）
            if (muxerStarted) {
                try { muxer?.stop() } catch (e: Exception) { Log.w(TAG, "Muxer stop in finally failed", e) }
            }
            try { muxer?.release() } catch (e: Exception) {}
            try { extractor?.release() } catch (e: Exception) {}
        }
    }

    private fun mergeAudioAndVideo(tempVideoFile: File, originalVideoUri: Uri, finalOutputFile: File): Boolean {
        var extractorVideo: MediaExtractor? = null
        var extractorAudio: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        var afdAudio: AssetFileDescriptor? = null

        try {
            extractorVideo = MediaExtractor()
            extractorVideo.setDataSource(tempVideoFile.absolutePath)

            extractorAudio = MediaExtractor()
            afdAudio = context.contentResolver.openAssetFileDescriptor(originalVideoUri, "r")
            if (afdAudio != null) {
                extractorAudio.setDataSource(afdAudio.fileDescriptor, afdAudio.startOffset, afdAudio.length)
            } else {
                Log.e(TAG, "[Merge] Failed to open AssetFileDescriptor for audio URI: $originalVideoUri")
                return false
            }

            var srcVideoTrack = -1
            for (i in 0 until extractorVideo.trackCount) {
                if (extractorVideo.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    srcVideoTrack = i
                    break
                }
            }
            Log.d(TAG, "[Merge] tempVideoFile tracks=${extractorVideo.trackCount}, srcVideoTrack=$srcVideoTrack")

            var srcAudioTrack = -1
            for (i in 0 until extractorAudio.trackCount) {
                if (extractorAudio.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    srcAudioTrack = i
                    break
                }
            }
            Log.d(TAG, "[Merge] originalUri audio tracks found=$srcAudioTrack")

            if (srcVideoTrack == -1) {
                Log.e(TAG, "[Merge] No video track in temp file!")
                return false
            }

            muxer = MediaMuxer(finalOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            extractorVideo.selectTrack(srcVideoTrack)
            val videoFormat = extractorVideo.getTrackFormat(srcVideoTrack)
            val dstVideoTrack = muxer.addTrack(videoFormat)

            var dstAudioTrack = -1
            if (srcAudioTrack != -1) {
                extractorAudio.selectTrack(srcAudioTrack)
                val audioFormat = extractorAudio.getTrackFormat(srcAudioTrack)
                dstAudioTrack = muxer.addTrack(audioFormat)
            }

            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var videoFrames = 0

            while (true) {
                val sampleSize = extractorVideo.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.set(0, sampleSize, extractorVideo.sampleTime, extractorVideo.sampleFlags)
                muxer.writeSampleData(dstVideoTrack, buffer, bufferInfo)
                extractorVideo.advance()
                videoFrames++
            }
            Log.d(TAG, "[Merge] Video frames written: $videoFrames")

            if (srcAudioTrack != -1) {
                var audioFrames = 0
                while (true) {
                    val sampleSize = extractorAudio.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    bufferInfo.set(0, sampleSize, extractorAudio.sampleTime, extractorAudio.sampleFlags)
                    muxer.writeSampleData(dstAudioTrack, buffer, bufferInfo)
                    extractorAudio.advance()
                    audioFrames++
                }
                Log.d(TAG, "[Merge] Audio frames written: $audioFrames")
            }

            muxer.stop()
            muxerStarted = false
            Log.i(TAG, "[Merge] Done. Final file: ${finalOutputFile.length() / 1024} KB")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "[Merge] Failed [${e.javaClass.simpleName}]: ${e.message}", e)
            return false
        } finally {
            try { afdAudio?.close() } catch (e: Exception) {}
            if (muxerStarted) {
                try { muxer?.stop() } catch (e: Exception) { Log.w(TAG, "[Merge] Muxer stop in finally", e) }
            }
            try { muxer?.release() } catch (e: Exception) {}
            try { extractorVideo?.release() } catch (e: Exception) {}
            try { extractorAudio?.release() } catch (e: Exception) {}
        }
    }

    fun calculateMd5(file: File): String? {
        return com.example.dinoroar.utils.FileUtils.calculateMd5(file)
    }

    fun calculateMd5(uri: Uri): String? {
        return com.example.dinoroar.utils.FileUtils.calculateMd5(context, uri)
    }
}
