package com.example.dinoroar.ui.lock

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dinoroar.data.local.ActivityStateTracker
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.data.sync.SyncManager

/**
 * 独立 Activity 锁屏防护包裹组件。
 * 监听生命周期 ON_STOP 事件，在锁屏或退至桌面时触发恐龙序列解锁。
 */
@Composable
fun DinoLockWrapper(
    securePrefs: SecurePrefs,
    syncManager: SyncManager? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var isAppLocked by remember { mutableStateOf(false) }
    var showNineGrid by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (ActivityStateTracker.isCameraActive || ActivityStateTracker.isExternalActivityActive) {
                    return@LifecycleEventObserver
                }
                val activity = context as? Activity
                if (activity != null && !activity.isFinishing) {
                    if (securePrefs.token != null) {
                        isAppLocked = true
                        showNineGrid = !securePrefs.isCamouflageEnabled
                    }
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (isAppLocked) {
            if (showNineGrid || !securePrefs.isCamouflageEnabled) {
                NineGridLockScreen(
                    correctPattern = securePrefs.lockPattern,
                    syncManager = syncManager,
                    onUnlockSuccess = {
                        isAppLocked = false
                        showNineGrid = false
                    },
                    onBackToGame = {
                        if (securePrefs.isCamouflageEnabled) {
                            showNineGrid = false
                        } else {
                            (context as? Activity)?.finish()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CamouflageGameScreen(
                    onTriggerUnlock = {
                        showNineGrid = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
