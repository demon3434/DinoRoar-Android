package com.example.dinoroar.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    private val SERVICE_TYPE = "_dinoroar._tcp"

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredService = MutableStateFlow<DiscoveredServerInfo?>(null)
    val discoveredService: StateFlow<DiscoveredServerInfo?> = _discoveredService.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun startDiscovery() {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredService.value = null

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("DinoRoarMulticastLock").apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire MulticastLock", e)
        }

        CoroutineScope(Dispatchers.IO).launch {
            var socket: java.net.DatagramSocket? = null
            try {
                socket = java.net.DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 3000
                }

                val requestData = "DISCOVER_DINOROAR_REQUEST".toByteArray(Charsets.UTF_8)
                val packet255 = java.net.DatagramPacket(
                    requestData,
                    requestData.size,
                    java.net.InetAddress.getByName("255.255.255.255"),
                    8090
                )

                // 1. 发送通用受限广播
                socket.send(packet255)
                delay(50)
                socket.send(packet255)

                // 2. 尝试计算并发送特定的 WiFi 子网广播以应对特定局域网环境
                try {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val dhcp = wifiManager.dhcpInfo
                    if (dhcp != null && dhcp.ipAddress != 0 && dhcp.netmask != 0) {
                        val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
                        val quads = ByteArray(4)
                        for (k in 0..3) {
                            quads[k] = ((broadcast shr (k * 8)) and 0xFF).toByte()
                        }
                        val subnetAddr = java.net.InetAddress.getByAddress(quads)
                        val packetSubnet = java.net.DatagramPacket(requestData, requestData.size, subnetAddr, 8090)
                        socket.send(packetSubnet)
                        delay(50)
                        socket.send(packetSubnet)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send subnet UDP broadcast", e)
                }

                val buffer = ByteArray(1024)
                val receivePacket = java.net.DatagramPacket(buffer, buffer.size)

                while (_isScanning.value) {
                    try {
                        socket.receive(receivePacket)
                        val responseText = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8).trim()

                        if (responseText.contains("port")) {
                            val json = org.json.JSONObject(responseText)
                            val servicePort = json.getInt("port")
                            val hostAddress = receivePacket.address.hostAddress

                            if (hostAddress != null) {
                                val info = DiscoveredServerInfo(
                                    hostAddress = hostAddress,
                                    port = servicePort
                                )
                                _discoveredService.value = info
                                break
                            }
                        }
                    } catch (timeout: java.net.SocketTimeoutException) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in UDP discovery loop", e)
            } finally {
                socket?.close()
                _isScanning.value = false
                releaseMulticastLock()
            }
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

    fun stopDiscovery() {
        _isScanning.value = false
        releaseMulticastLock()
    }
}
