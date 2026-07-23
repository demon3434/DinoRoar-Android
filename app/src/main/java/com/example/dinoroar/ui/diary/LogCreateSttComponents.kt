package com.example.dinoroar.ui.diary

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.dinoroar.media.AudioRecorder
import com.example.dinoroar.media.DiagnosticLogger
import com.example.dinoroar.network.DinoApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

private object Log {
    fun d(tag: String, msg: String) { DiagnosticLogger.log(tag, "D", msg) }
    fun i(tag: String, msg: String) { DiagnosticLogger.log(tag, "I", msg) }
    fun w(tag: String, msg: String, tr: Throwable? = null) { DiagnosticLogger.log(tag, "W", msg, tr) }
    fun e(tag: String, msg: String, tr: Throwable? = null) { DiagnosticLogger.log(tag, "E", msg, tr) }
}

enum class SttTargetField {
    TITLE,
    CONTENT,
    OWN_THOUGHTS
}

/**
 * 屏幕中央半透明音浪与手势提示 HUD (Center Wave HUD)
 */
@Composable
fun CenterWaveHud(
    isRecording: Boolean,
    isTranscribing: Boolean,
    isCancelHovered: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isCancelHovered) Color(0xD98B0000) else Color(0xD91E2235),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (isCancelHovered) Color(0xFFFF5555) else Color(0xFF00E5FF)
            ),
            shadowElevation = 8.dp,
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        isTranscribing -> "🦖 小恐龙正在识别中..."
                        isCancelHovered -> "⚠️ 松开手指，取消识别"
                        else -> "🎙️ 小恐龙正在倾听..."
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isTranscribing) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(32.dp)
                    ) {
                        val barHeights = if (isCancelHovered) {
                            listOf(8.dp, 12.dp, 8.dp, 12.dp, 8.dp)
                        } else {
                            listOf(16.dp, 28.dp, 20.dp, 32.dp, 18.dp)
                        }
                        barHeights.forEach { height ->
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(height)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isCancelHovered) Color(0xFFFF5555) else Color(0xFF00E5FF))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = when {
                        isTranscribing -> "请稍等片刻，文字即将呈现"
                        isCancelHovered -> "释放后将直接丢弃本次录音"
                        else -> "💡 向上滑动手指可取消语音录制"
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * 带着 🎙️ 图标的语音转写交互胶囊按钮
 */
@Composable
fun VoiceInputButton(
    targetField: SttTargetField,
    audioRecorder: AudioRecorder,
    apiService: DinoApiService,
    onAppendText: (SttTargetField, String) -> Unit,
    onHudStateChange: (active: Boolean, recording: Boolean, transcribing: Boolean, cancel: Boolean) -> Unit,
    onPressStart: ((SttTargetField) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var isCancelHovered by remember { mutableStateOf(false) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    fun startRecordingInternal() {
        Log.i("VoiceInputButton", "Starting STT voice recording for targetField: $targetField")
        val f = audioRecorder.startRecording()
        if (f != null) {
            recordedFile = f
            isRecording = true
            isCancelHovered = false
            dragOffsetY = 0f
            onHudStateChange(true, true, false, false)
            Log.i("VoiceInputButton", "STT recording started successfully, file: ${f.absolutePath}")
        } else {
            Log.e("VoiceInputButton", "Failed to start AudioRecorder for STT")
            Toast.makeText(context, "无法启动录音设备", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("VoiceInputButton", "RECORD_AUDIO permission granted by user")
            startRecordingInternal()
        } else {
            Log.w("VoiceInputButton", "RECORD_AUDIO permission denied by user")
            Toast.makeText(context, "语音功能需要麦克风录音权限哦！", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            isTranscribing -> Color(0xFF1E2A38)
            isRecording && isCancelHovered -> Color(0xFF8B0000)
            isRecording -> Color(0xFFD32F2F)
            else -> Color(0xFF2A2D3E)
        },
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isRecording) Color(0xFFFF5555) else Color(0xFF3F445D)
        ),
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onPressStart?.invoke(targetField)
                    
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasPerm) {
                        Log.i("VoiceInputButton", "Requesting RECORD_AUDIO permission...")
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        continue
                    }

                    startRecordingInternal()
                    var isCancelled = false
                    val startY = down.position.y

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointerChange = event.changes.firstOrNull { it.id == down.id }

                        if (pointerChange == null || !pointerChange.pressed) {
                            break
                        }
                        pointerChange.consume()

                        val dragDistanceY: Float = pointerChange.position.y - startY
                        val currentCancelled = dragDistanceY < -200.0f
                        if (isCancelHovered != currentCancelled && isRecording) {
                            isCancelHovered = currentCancelled
                            onHudStateChange(true, true, false, currentCancelled)
                        }
                        isCancelled = currentCancelled
                    }

                    if (isRecording) {
                        isRecording = false
                        val fileToTranscribe = audioRecorder.stopRecording()

                        if (isCancelled || fileToTranscribe == null || !fileToTranscribe.exists()) {
                            Log.i("VoiceInputButton", "STT voice recording cancelled or file invalid. Cancelled=$isCancelled")
                            fileToTranscribe?.delete()
                            onHudStateChange(false, false, false, false)
                        } else if (fileToTranscribe.length() < 5000) {
                            Log.i("VoiceInputButton", "STT audio file too short (${fileToTranscribe.length()} bytes), skipping API call")
                            fileToTranscribe.delete()
                            onHudStateChange(false, false, false, false)
                            Toast.makeText(context, "说话时间太短啦，请长按说话哦～", Toast.LENGTH_SHORT).show()
                        } else {
                            isTranscribing = true
                            onHudStateChange(true, false, true, false)
                            Log.i("VoiceInputButton", "Sending STT audio file (${fileToTranscribe.length()} bytes) to server...")

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val reqBody = fileToTranscribe.asRequestBody("audio/m4a".toMediaTypeOrNull())
                                    val part = MultipartBody.Part.createFormData("file", fileToTranscribe.name, reqBody)
                                    val res = apiService.transcribeAudio(part)
                                    Log.i("VoiceInputButton", "STT response received: text='${res.text}', emotion='${res.emotion}'")

                                    val rawText = res.text.trim()
                                    val cleanText = rawText.replace(Regex("^[?。.,！!？\\s]+$"), "")

                                    withContext(Dispatchers.Main) {
                                        if (cleanText.isNotBlank()) {
                                            onAppendText(targetField, rawText)
                                            Toast.makeText(context, "✨ 语音转换成功！", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Log.w("VoiceInputButton", "STT returned empty or noise-only text: '$rawText'")
                                            Toast.makeText(context, "没听清，请长按说话再试一次哦～", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("VoiceInputButton", "STT transcription API failed: ${e.message}", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "语音识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    fileToTranscribe.delete()
                                    withContext(Dispatchers.Main) {
                                        isTranscribing = false
                                        onHudStateChange(false, false, false, false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎙️",
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = when {
                    isTranscribing -> "转译中"
                    isRecording && isCancelHovered -> "松开取消"
                    isRecording -> "松开识别"
                    else -> "按住说话"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
