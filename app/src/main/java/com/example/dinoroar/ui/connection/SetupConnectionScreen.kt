package com.example.dinoroar.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.network.ConnectionManager
import com.example.dinoroar.network.ConnectionState
import com.example.dinoroar.network.NsdHelper
import com.example.dinoroar.network.DiscoveredServerInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.dinoroar.theme.LocalAppColors


@Composable
fun SetupConnectionScreen(
    nsdHelper: NsdHelper,
    connectionManager: ConnectionManager,
    onConnected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isScanning by nsdHelper.isScanning.collectAsState()
    val discoveredService by nsdHelper.discoveredService.collectAsState()
    val connectionState by connectionManager.connectionState.collectAsState()

    var manualIp by remember { mutableStateOf("http://192.168.1.100:8080") }
    var showManualForm by remember { mutableStateOf(false) }

    // Start discovery on launch
    LaunchedEffect(Unit) {
        nsdHelper.startDiscovery()
        connectionManager.setScanning()
        
        // Timeout fallback to manual input after 8 seconds
        delay(8000)
        if (connectionState is ConnectionState.Scanning || connectionState is ConnectionState.Idle) {
            showManualForm = true
            nsdHelper.stopDiscovery()
        }
    }

    // React to service discovery
    LaunchedEffect(discoveredService) {
        discoveredService?.let { service ->
            val hostIp = service.hostAddress
            val port = service.port
            nsdHelper.stopDiscovery()
            coroutineScope.launch {
                val url = "http://$hostIp:$port"
                val success = connectionManager.checkConnection(url)
                if (success) {
                    onConnected(url)
                } else {
                    showManualForm = true
                }
            }
        }
    }

    val appColors = LocalAppColors.current
    val darkBg = appColors.darkBg
    val neonGreen = appColors.neonGreen
    val neonAmber = appColors.neonAmber
    val cardBg = appColors.cardBg
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🌐 局域网基舱直连",
                    color = neonAmber,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(16.dp))

                when (val state = connectionState) {
                    is ConnectionState.Scanning -> {
                        CircularProgressIndicator(color = neonGreen)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "正在扫描家庭局域网内的秘密服务器...",
                            color = textSecondary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    is ConnectionState.Checking -> {
                        CircularProgressIndicator(color = neonGreen)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "发现服务器！正在握手测试中...",
                            color = textSecondary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    is ConnectionState.Connected -> {
                        Text(
                            text = "已成功连接至基舱:\n${state.baseUrl}",
                            color = neonGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    is ConnectionState.Failed -> {
                        Text(
                            text = "连接失败: ${state.error ?: "未知网络错误"}",
                            color = Color.Red,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                nsdHelper.startDiscovery()
                                connectionManager.setScanning()
                                showManualForm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = neonAmber)
                        ) {
                            Text("重新扫描", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {}
                }

                if (showManualForm || connectionState is ConnectionState.Failed) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "或者手动输入服务器 IP 地址:",
                        color = textSecondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("服务器 URL", color = textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = textSecondary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val success = connectionManager.checkConnection(manualIp)
                                if (success) {
                                    onConnected(manualIp)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("手动建立连接", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
