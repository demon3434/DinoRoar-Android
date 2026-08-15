package com.example.dinoroar.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredServerInfo(
    val hostAddress: String,
    val port: Int
)

@Singleton
class NsdHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "NsdHelper"
    private val SERVICE_TYPE = "_dinoroar._tcp."

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var scanTimeoutJob: Job? = null

    private val _discoveredService = MutableStateFlow<DiscoveredServerInfo?>(null)
    val discoveredService: StateFlow<DiscoveredServerInfo?> = _discoveredService.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /**
     * 开始扫描局域网内的 DinoRoar 服务 (标准 mDNS / NSD)
     */
    fun startDiscovery() {
        if (_isScanning.value) {
            Log.d(TAG, "mDNS discovery already running.")
            return
        }

        stopDiscovery()
        _isScanning.value = true
        _discoveredService.value = null

        acquireMulticastLock()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS Service discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "mDNS Service found: ${service.serviceName}, type: ${service.serviceType}")
                if (service.serviceType.contains("_dinoroar._tcp")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "mDNS Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "mDNS Discovery stopped: $serviceType")
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "mDNS Discovery start failed: Error code $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "mDNS Discovery stop failed: Error code $errorCode")
                stopDiscovery()
            }
        }
        discoveryListener = listener

        try {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking discoverServices", e)
            stopDiscovery()
        }

        // 超时 8 秒自动停止扫描
        scanTimeoutJob = CoroutineScope(Dispatchers.IO).launch {
            delay(8000L)
            if (_isScanning.value) {
                Log.d(TAG, "Discovery scan timeout reached.")
                stopDiscovery()
            }
        }
    }

    /**
     * 解析发现到的服务节点信息
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                nsdManager.registerServiceInfoCallback(
                    serviceInfo,
                    context.mainExecutor,
                    object : NsdManager.ServiceInfoCallback {
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                            Log.e(TAG, "ServiceInfo callback registration failed: $errorCode")
                        }

                        override fun onServiceUpdated(info: NsdServiceInfo) {
                            handleResolvedService(info)
                            try {
                                nsdManager.unregisterServiceInfoCallback(this)
                            } catch (_: Exception) {}
                        }

                        override fun onServiceLost() {}

                        override fun onServiceInfoCallbackUnregistered() {}
                    }
                )
            } else {
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Resolve failed for ${service.serviceName}: error $errorCode")
                    }

                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        handleResolvedService(resolvedInfo)
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve service", e)
        }
    }

    /**
     * 提取解析出的 IP 与端口
     */
    private fun handleResolvedService(resolvedInfo: NsdServiceInfo) {
        val host: InetAddress? = resolvedInfo.host
        val hostAddress = host?.hostAddress ?: return
        val port = resolvedInfo.port

        if (port <= 0) return

        // 优先从 TXT 记录提取 host/mappedPort 属性（针对容器 NAT 映射场景）
        var finalHost = hostAddress
        var finalPort = port

        val attributes = resolvedInfo.attributes
        if (attributes != null && attributes.isNotEmpty()) {
            attributes["host"]?.let { bytes ->
                val txtHost = String(bytes, Charsets.UTF_8).trim()
                if (txtHost.isNotEmpty()) {
                    finalHost = txtHost
                }
            }
            attributes["mappedPort"]?.let { bytes ->
                val txtPort = String(bytes, Charsets.UTF_8).trim().toIntOrNull()
                if (txtPort != null && txtPort > 0) {
                    finalPort = txtPort
                }
            }
        }

        Log.i(TAG, "Successfully resolved DinoRoar server at: $finalHost:$finalPort")
        _discoveredService.value = DiscoveredServerInfo(
            hostAddress = finalHost,
            port = finalPort
        )
        // 成功发现服务后自动停止扫描
        stopDiscovery()
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wifiManager.createMulticastLock("DinoRoarMdnsLock").apply {
                    setReferenceCounted(true)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire MulticastLock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release MulticastLock", e)
        } finally {
            multicastLock = null
        }
    }

    /**
     * 停止扫描
     */
    fun stopDiscovery() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null

        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping service discovery: ${e.message}")
            }
            discoveryListener = null
        }

        _isScanning.value = false
        releaseMulticastLock()
    }
}

