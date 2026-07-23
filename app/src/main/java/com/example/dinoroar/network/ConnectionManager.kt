package com.example.dinoroar.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ConnectionState {
    object Idle : ConnectionState
    object Scanning : ConnectionState
    object Checking : ConnectionState
    data class Connected(val baseUrl: String) : ConnectionState
    data class Failed(val error: String?) : ConnectionState
}

@Singleton
class ConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "ConnectionManager"
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun setScanning() {
        _connectionState.value = ConnectionState.Scanning
    }

    fun setIdle() {
        _connectionState.value = ConnectionState.Idle
    }

    suspend fun checkConnection(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Checking
        val cleanUrl = baseUrl.removeSuffix("/")
        val request = Request.Builder()
            .url("$cleanUrl/api/health")
            .get()
            .build()
        
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    _connectionState.value = ConnectionState.Connected(cleanUrl)
                    true
                } else {
                    Log.e(TAG, "Connection probe failed with code: ${response.code}")
                    _connectionState.value = ConnectionState.Failed("Server responded with code: ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection exception: ${e.message}")
            _connectionState.value = ConnectionState.Failed(e.message)
            false
        }
    }

    suspend fun checkConnectionSilent(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.removeSuffix("/")
        val request = Request.Builder()
            .url("$cleanUrl/api/health")
            .get()
            .build()
        
        try {
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Silent probe exception: ${e.message}")
            false
        }
    }

    fun isWifiConnected(): Boolean {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking wifi connection status: ${e.message}")
            return false
        }
    }

    fun forceConnection(baseUrl: String) {
        val cleanUrl = baseUrl.removeSuffix("/")
        _connectionState.value = ConnectionState.Connected(cleanUrl)
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.Failed("Disconnected manually")
    }
}
