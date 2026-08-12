package com.example.dinoroar.ui.diary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.local.ActivityStateTracker
import com.example.dinoroar.data.local.CanvasInstanceDao
import com.example.dinoroar.data.local.CanvasSeriesDao
import com.example.dinoroar.data.local.CanvasSetDao
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.theme.DinoRoarTheme
import com.example.dinoroar.theme.LocalAppColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CanvasExchangeActivity : ComponentActivity() {

    @Inject
    lateinit var apiService: DinoApiService

    @Inject
    lateinit var securePrefs: SecurePrefs

    @Inject
    lateinit var canvasSeriesDao: CanvasSeriesDao

    @Inject
    lateinit var canvasSetDao: CanvasSetDao

    @Inject
    lateinit var canvasInstanceDao: CanvasInstanceDao

    override fun onStop() {
        super.onStop()
        ActivityStateTracker.isExternalActivityActive = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeId = remember { securePrefs.currentThemeId }
            DinoRoarTheme(themeId = themeId) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CanvasExchangeScreen(
                        apiService = apiService,
                        securePrefs = securePrefs,
                        canvasSeriesDao = canvasSeriesDao,
                        canvasSetDao = canvasSetDao,
                        canvasInstanceDao = canvasInstanceDao,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
