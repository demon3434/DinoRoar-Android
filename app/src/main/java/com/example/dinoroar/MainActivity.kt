package com.example.dinoroar

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.dinoroar.theme.DinoRoarTheme
import com.example.dinoroar.network.NsdHelper
import com.example.dinoroar.network.ConnectionManager
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.sync.SyncManager
import com.example.dinoroar.media.AudioRecorder
import com.example.dinoroar.media.MediaCompressor
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var nsdHelper: NsdHelper
  @Inject lateinit var connectionManager: ConnectionManager
  @Inject lateinit var securePrefs: SecurePrefs
  @Inject lateinit var apiService: DinoApiService
  @Inject lateinit var repository: DataRepository
  @Inject lateinit var syncManager: SyncManager
  @Inject lateinit var audioRecorder: AudioRecorder
  @Inject lateinit var mediaCompressor: MediaCompressor

  private lateinit var connectivityManager: ConnectivityManager
  private var networkCallback: ConnectivityManager.NetworkCallback? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
 
    connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    registerNetworkCallback()

    enableEdgeToEdge()
    setContent {
      val currentUserId by securePrefs.currentUserIdFlow.collectAsState(initial = securePrefs.username)
      var themeId by remember { mutableStateOf(securePrefs.currentThemeId) }

      LaunchedEffect(currentUserId) {
          themeId = securePrefs.currentThemeId
      }

      DinoRoarTheme(themeId = themeId) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
            nsdHelper = nsdHelper,
            connectionManager = connectionManager,
            securePrefs = securePrefs,
            apiService = apiService,
            repository = repository,
            syncManager = syncManager,
            audioRecorder = audioRecorder,
            mediaCompressor = mediaCompressor,
            onThemeChanged = { themeId = it }
          )
        }
      }
    }
  }

  private fun registerNetworkCallback() {
    val request = NetworkRequest.Builder()
        .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    networkCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        triggerIntranetCheck()
      }
    }
    
    try {
      connectivityManager.registerNetworkCallback(request, networkCallback!!)
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "Failed to register network callback: ${e.message}")
    }
  }

  private fun triggerIntranetCheck() {
    val intranetUrl = securePrefs.intranetUrl
    val extranetUrl = securePrefs.extranetUrl
    val currentUrl = securePrefs.serverUrl

    if (!intranetUrl.isNullOrBlank() && !extranetUrl.isNullOrBlank() &&
        currentUrl == extranetUrl && intranetUrl != extranetUrl) {
      
      if (connectionManager.isWifiConnected()) {
        lifecycleScope.launch {
          val isIntranetAccessible = connectionManager.checkConnectionSilent(intranetUrl)
          if (isIntranetAccessible) {
            securePrefs.serverUrl = intranetUrl
            connectionManager.forceConnection(intranetUrl)
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    triggerIntranetCheck()
  }

  override fun onDestroy() {
    super.onDestroy()
    networkCallback?.let {
      try {
        connectivityManager.unregisterNetworkCallback(it)
      } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to unregister network callback: ${e.message}")
      }
    }
  }
}
