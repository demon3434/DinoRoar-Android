package com.example.dinoroar.ui.diary

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.dinoroar.network.MediaCacheManager
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialog(
    videoUrl: String,
    token: String?,
    title: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember(videoUrl) {
        com.example.dinoroar.media.DiagnosticLogger.log("VideoPlayerDialog", "I", "Initializing ExoPlayer for URL: $videoUrl")
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1_500,  // minBufferMs (降低最小缓冲)
                6_000,  // maxBufferMs (限制最大预缓冲为 6 秒)
                300,    // bufferForPlaybackMs (仅缓冲 300ms 即渲染起播)
                500     // bufferForPlaybackAfterRebufferMs (Seek卡顿恢复降至 500ms)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                val cacheFactory = MediaCacheManager.createCacheDataSourceFactory(context, token)
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(videoUrl))
                .setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP4)
                .build()
            val mediaSource = ProgressiveMediaSource.Factory(cacheFactory)
                .createMediaSource(mediaItem)
            setMediaSource(mediaSource)
            
            // 添加播放状态监听器，记录缓冲和就绪时间点
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    val stateStr = when(state) {
                        androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                        androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                        androidx.media3.common.Player.STATE_READY -> "READY"
                        androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    com.example.dinoroar.media.DiagnosticLogger.log("VideoPlayerDialog", "I", "Playback state changed: $stateStr")
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    com.example.dinoroar.media.DiagnosticLogger.log("VideoPlayerDialog", "E", "Player error: ${error.message}", error)
                }

                override fun onIsLoadingChanged(isLoading: Boolean) {
                    com.example.dinoroar.media.DiagnosticLogger.log("VideoPlayerDialog", "I", "Is loading changed: $isLoading")
                }

                override fun onPositionDiscontinuity(
                    oldPosition: androidx.media3.common.Player.PositionInfo,
                    newPosition: androidx.media3.common.Player.PositionInfo,
                    reason: Int
                ) {
                    val reasonStr = when(reason) {
                        androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
                        androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
                        androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
                        androidx.media3.common.Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
                        else -> "OTHER($reason)"
                    }
                    com.example.dinoroar.media.DiagnosticLogger.log(
                        "VideoPlayerDialog",
                        "I",
                        "Position discontinuity: reason=$reasonStr, oldPos=${oldPosition.positionMs}ms, newPos=${newPosition.positionMs}ms"
                    )
                }
            })

            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val view = LocalView.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val window = (view.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window?.let { w ->
                w.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                w.statusBarColor = android.graphics.Color.TRANSPARENT
                w.navigationBarColor = android.graphics.Color.TRANSPARENT
                
                val controller = androidx.core.view.WindowCompat.getInsetsController(w, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!title.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(bottom = 60.dp, start = 16.dp, end = 16.dp, top = 16.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}
