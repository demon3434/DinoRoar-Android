package com.example.dinoroar.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.ConnectionManager
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.network.NsdHelper
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    nsdHelper: NsdHelper,
    connectionManager: ConnectionManager,
    apiService: DinoApiService,
    securePrefs: SecurePrefs,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // 账号密码状态
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // 内外网双服务器 URL 状态 (读取本地已持久化记录的 IP，若为空则显示默认示例占位)
    var intranetUrl by remember { mutableStateOf(securePrefs.intranetUrl ?: "http://192.168.1.100:8080") }
    var extranetUrl by remember { mutableStateOf(securePrefs.extranetUrl ?: "http://yourdomain.com:8080") }

    // NSD 局域网服务后台静默发现状态
    val discoveredService by nsdHelper.discoveredService.collectAsState()
    var autoDiscoveredLabel by remember { mutableStateOf<String?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 后台静默启动局域网 NSD 扫描，绝对不影响界面交互及文本输入
    LaunchedEffect(Unit) {
        nsdHelper.startDiscovery()
    }

    // 监听 NSD 后台发现，自动静默回填内网输入框，不影响打字
    LaunchedEffect(discoveredService) {
        discoveredService?.let { service ->
            val hostIp = service.hostAddress
            val port = service.port
            val discoveredUrl = "http://$hostIp:$port"
            // 若内网输入框当前未做手动修改，或者内容与默认占位相同，则静默更新回填并给出绿色标签提示
            if (intranetUrl.isEmpty() || intranetUrl == "http://192.168.1.100:8080") {
                intranetUrl = discoveredUrl
                autoDiscoveredLabel = "（已自动发现局域网服务器）"
            }
        }
    }

    // 关闭页面时，安全注销 NSD 监听
    DisposableEffect(Unit) {
        onDispose {
            nsdHelper.stopDiscovery()
        }
    }

    // 暗黑科幻暖金配色
    val darkBg = Color(0xFF121214) // 纯粹夜空黑
    val panelBg = Color(0xFF1E1E22) // 卡片哑光背景
    val neonBlue = Color(0xFF00E5FF) // 霓虹天蓝
    val neonGreen = Color(0xFF00FF66) // 霓虹亮绿
    val neonAmber = Color(0xFFFFB300) // 琥珀金

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .verticalScroll(scrollState),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = panelBg)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🦕 登录秘密舱室 🦖",
                    color = neonAmber,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(20.dp))

                // 1. 孩子账号文本框
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("孩子账号", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = neonBlue,
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. 解锁密码文本框
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("登录密码", color = Color.Gray) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = neonBlue,
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // 3. 内网服务器 URL 文本框
                OutlinedTextField(
                    value = intranetUrl,
                    onValueChange = { 
                        intranetUrl = it 
                        autoDiscoveredLabel = null // 手动修改时抹除自动标签
                    },
                    label = { Text("内网服务器 URL (局域网优先)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = neonGreen,
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 自动发现绿色提示标签
                if (autoDiscoveredLabel != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = autoDiscoveredLabel!!,
                        color = neonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                val isScanning by nsdHelper.isScanning.collectAsState()
                val context = androidx.compose.ui.platform.LocalContext.current

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        nsdHelper.startDiscovery()
                        errorMessage = null
                        val currentDiscovered = nsdHelper.discoveredService.value
                        if (currentDiscovered != null) {
                            val hostIp = currentDiscovered.hostAddress
                            val port = currentDiscovered.port
                            intranetUrl = "http://$hostIp:$port"
                            autoDiscoveredLabel = "（已自动发现局域网服务器）"
                            android.widget.Toast.makeText(context, "已成功发现服务器并回填！", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        android.widget.Toast.makeText(context, "正在寻找局域网内秘密服务器...", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isScanning) "🔍 正在自动发现中..." else "🔍 自动发现内网服务",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. 外网服务器 URL 文本框
                OutlinedTextField(
                    value = extranetUrl,
                    onValueChange = { extranetUrl = it },
                    label = { Text("外网服务器 URL (公网直连)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = neonGreen,
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 错误报警区域
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 5. 登录按钮 (开启网络连接联级检测：内网优先 -> 失败降级外网 -> 登录)
                if (isLoading) {
                    CircularProgressIndicator(color = neonBlue)
                } else {
                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                errorMessage = "请输入账号和密码"
                                return@Button
                            }
                            if (intranetUrl.isBlank() && extranetUrl.isBlank()) {
                                errorMessage = "请输入至少一个服务器 URL"
                                return@Button
                            }

                            isLoading = true
                            errorMessage = null

                            coroutineScope.launch {
                                var connectedUrl: String? = null

                                // 步骤 5a. 优先检测并握手内网
                                if (intranetUrl.isNotBlank()) {
                                    val checkedIntranet = intranetUrl.trim()
                                    val success = connectionManager.checkConnection(checkedIntranet)
                                    if (success) {
                                        connectedUrl = checkedIntranet
                                    }
                                }

                                // 步骤 5b. 若内网不通，降级检测外网
                                if (connectedUrl == null && extranetUrl.isNotBlank()) {
                                    val checkedExtranet = extranetUrl.trim()
                                    val success = connectionManager.checkConnection(checkedExtranet)
                                    if (success) {
                                        connectedUrl = checkedExtranet
                                    }
                                }

                                // 步骤 5c. 判定握手连接结果
                                if (connectedUrl != null) {
                                    // 握手成功：保存内外网配置值回显，并记录本次最终连接成功的 serverUrl
                                    securePrefs.serverUrl = connectedUrl
                                    securePrefs.intranetUrl = intranetUrl.trim()
                                    securePrefs.extranetUrl = extranetUrl.trim()

                                    try {
                                        // 执行登录
                                        val tokenRes = apiService.login(username, password)
                                        securePrefs.token = tokenRes.access_token
                                        securePrefs.username = username

                                        // 同步提取并保存锁屏密码序列
                                        val profile = apiService.getMe()
                                        securePrefs.lockPattern = profile.lock_pattern
                                        securePrefs.nickname = profile.nickname ?: ""

                                        isLoading = false
                                        onLoginSuccess()
                                    } catch (e: Exception) {
                                        isLoading = false
                                        errorMessage = "登录失败: ${e.message ?: "用户名或密码错误"}"
                                    }
                                } else {
                                    isLoading = false
                                    errorMessage = "连接服务器失败：内外网均不可达，请检查网络设置及地址"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = neonBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("进入避难所", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
