package com.example.dinoroar.ui.settings

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.widget.Toast
import com.example.dinoroar.media.DiagnosticLogger
import com.example.dinoroar.data.DataRepository
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.theme.LocalAppColors
import com.example.dinoroar.theme.ThemeList
import com.example.dinoroar.network.NsdHelper
import com.example.dinoroar.network.DiscoveredServerInfo
import androidx.activity.compose.BackHandler

enum class SettingMenuState {
    MAIN,                 // 一级菜单
    DINO_LOCK,            // 恐龙解锁设置 (二级)
    THEME_SELECT,         // 选择主题配色 (二级)
    SERVER_ADDRESS,       // 修改服务器地址 (二级)
    MEDIA_SETTINGS,       // 多媒体附件设置 (二级)
    VIDEO_QUALITY,        // 视频压缩档位 (三级)
    DIAGNOSTIC_LOG        // 诊断日志页面 (二级)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nsdHelper: NsdHelper,
    securePrefs: SecurePrefs,
    repository: DataRepository,
    onThemeChanged: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToEditPattern: () -> Unit,
    onLogout: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var menuState by remember { mutableStateOf(SettingMenuState.MAIN) }

    val appColors = LocalAppColors.current
    val darkBg = appColors.darkBg
    val cardBg = appColors.cardBg
    val neonBlue = appColors.neonBlue
    val neonAmber = appColors.neonAmber
    val neonRed = appColors.neonRed
    val neonGreen = appColors.neonGreen
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary

    // 页面级联返回键处理
    val handleBackClick = {
        when (menuState) {
            SettingMenuState.MAIN -> onNavigateBack()
            SettingMenuState.DINO_LOCK -> menuState = SettingMenuState.MAIN
            SettingMenuState.THEME_SELECT -> menuState = SettingMenuState.MAIN
            SettingMenuState.SERVER_ADDRESS -> menuState = SettingMenuState.MAIN
            SettingMenuState.MEDIA_SETTINGS -> menuState = SettingMenuState.MAIN
            SettingMenuState.VIDEO_QUALITY -> menuState = SettingMenuState.MEDIA_SETTINGS
            SettingMenuState.DIAGNOSTIC_LOG -> menuState = SettingMenuState.MAIN
        }
    }

    // 拦截物理返回按键/滑动手势。如果是非一级设置菜单则回退到上一级菜单；如果已是一级设置菜单则直接退回主页。
    // 始终保持 enabled = true，避免由于状态切换导致 Android 系统 OnBackPressedDispatcher 回调响应延迟漏掉事件。
    BackHandler(enabled = true) {
        if (menuState != SettingMenuState.MAIN) {
            handleBackClick()
        } else {
            onNavigateBack()
        }
    }

    AnimatedContent(
        targetState = menuState,
        transitionSpec = {
            val isPop = when {
                initialState != SettingMenuState.MAIN && targetState == SettingMenuState.MAIN -> true
                initialState == SettingMenuState.VIDEO_QUALITY && targetState == SettingMenuState.MEDIA_SETTINGS -> true
                else -> false
            }
            if (isPop) {
                slideInHorizontally(
                    animationSpec = tween(300),
                    initialOffsetX = { -it }
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(300),
                    targetOffsetX = { it }
                )
            } else {
                slideInHorizontally(
                    animationSpec = tween(300),
                    initialOffsetX = { it }
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(300),
                    targetOffsetX = { -it }
                )
            }
        },
        label = "MenuTransition",
        modifier = modifier.fillMaxSize()
    ) { targetState ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (targetState) {
                                SettingMenuState.MAIN -> "安全基舱设置"
                                SettingMenuState.DINO_LOCK -> "恐龙序列解锁设置"
                                SettingMenuState.THEME_SELECT -> "主题颜色设置"
                                SettingMenuState.SERVER_ADDRESS -> "服务器地址配置"
                                SettingMenuState.MEDIA_SETTINGS -> "多媒体附件参数"
                                SettingMenuState.VIDEO_QUALITY -> "视频压缩质量"
                                SettingMenuState.DIAGNOSTIC_LOG -> "诊断与调试日志"
                            },
                            color = neonAmber,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { handleBackClick() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = neonAmber)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
                )
            },
            containerColor = darkBg
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (targetState) {
                    SettingMenuState.MAIN -> {
                    // ================= 一级菜单主页 =================

                    // 1. 🎮 游戏伪装卡片
                    var isCamouflageEnabled by remember { mutableStateOf(securePrefs.isCamouflageEnabled) }
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // 🎮 开启游戏伪装
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🎮 开启游戏伪装",
                                        color = textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "未开启时，启动应用直接进行九宫格解锁",
                                        color = textSecondary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Switch(
                                    checked = isCamouflageEnabled,
                                    onCheckedChange = {
                                        isCamouflageEnabled = it
                                        securePrefs.isCamouflageEnabled = it
                                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = neonAmber,
                                        checkedTrackColor = neonAmber.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            HorizontalDivider(color = textSecondary.copy(alpha = 0.12f), modifier = Modifier.padding(horizontal = 16.dp))

                            // 2. 🔒 恐龙解锁设置入口
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { menuState = SettingMenuState.DINO_LOCK }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🔒 恐龙解锁设置",
                                        color = textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "显示与修改恐龙绘制解锁序列",
                                        color = textSecondary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = textSecondary
                                )
                            }
                            HorizontalDivider(color = textSecondary.copy(alpha = 0.12f), modifier = Modifier.padding(horizontal = 16.dp))

                            // 3. 🎨 主题配色选择入口
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { menuState = SettingMenuState.THEME_SELECT }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🎨 主题颜色",
                                        color = textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val currentThemeName = ThemeList.find { it.id == securePrefs.currentThemeId }?.name ?: "默认暗色"
                                    Text(
                                        text = "当前主题: $currentThemeName",
                                        color = textSecondary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = textSecondary
                                )
                            }
                            HorizontalDivider(color = textSecondary.copy(alpha = 0.12f), modifier = Modifier.padding(horizontal = 16.dp))

                            // 4. 🌐 服务器地址配置入口
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { menuState = SettingMenuState.SERVER_ADDRESS }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🌐 秘密基地服务器配置",
                                        color = textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "修改内网与外网数据同步服务器地址",
                                        color = textSecondary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = textSecondary
                                )
                            }
                            HorizontalDivider(color = textSecondary.copy(alpha = 0.12f), modifier = Modifier.padding(horizontal = 16.dp))

                            // 5. 📂 多媒体附件管理入口
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { menuState = SettingMenuState.MEDIA_SETTINGS }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📂 多媒体附件管理",
                                        color = textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "设置同步网络与视频压缩质量",
                                        color = textSecondary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = textSecondary
                                )
                            }
                            HorizontalDivider(color = textSecondary.copy(alpha = 0.12f), modifier = Modifier.padding(horizontal = 16.dp))

                            // 6. 📋 诊断与调试日志入口
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { menuState = SettingMenuState.DIAGNOSTIC_LOG }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📋 诊断与调试日志",
                                        color = textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "查看与复制多媒体视频压缩的调试日志",
                                        color = textSecondary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Logout Buttons
                    Button(
                        onClick = { onLogout() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("退出账户登录", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onDisconnect() },
                        colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("断开服务器连接", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                    SettingMenuState.DINO_LOCK -> {
                        DinoLockSettingsSection(
                            securePrefs = securePrefs,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            neonBlue = neonBlue,
                            cardBg = cardBg,
                            onNavigateToEditPattern = onNavigateToEditPattern
                        )
                    }

                    SettingMenuState.THEME_SELECT -> {
                        ThemeSelectSettingsSection(
                            securePrefs = securePrefs,
                            textPrimary = textPrimary,
                            neonBlue = neonBlue,
                            neonGreen = neonGreen,
                            neonAmber = neonAmber,
                            cardBg = cardBg,
                            onThemeChanged = onThemeChanged
                        )
                    }

                    SettingMenuState.SERVER_ADDRESS -> {
                        ServerAddressSettingsSection(
                            securePrefs = securePrefs,
                            nsdHelper = nsdHelper,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            neonBlue = neonBlue,
                            neonGreen = neonGreen,
                            neonAmber = neonAmber,
                            cardBg = cardBg,
                            onSaveComplete = { menuState = SettingMenuState.MAIN }
                        )
                    }

                    SettingMenuState.MEDIA_SETTINGS -> {
                        MediaSettingsSection(
                            securePrefs = securePrefs,
                            repository = repository,
                            coroutineScope = coroutineScope,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            neonAmber = neonAmber,
                            cardBg = cardBg,
                            onNavigateToVideoQuality = { menuState = SettingMenuState.VIDEO_QUALITY }
                        )
                    }

                    SettingMenuState.VIDEO_QUALITY -> {
                        VideoQualitySettingsSection(
                            securePrefs = securePrefs,
                            textPrimary = textPrimary,
                            neonBlue = neonBlue,
                            cardBg = cardBg,
                            onQualitySelected = { menuState = SettingMenuState.MEDIA_SETTINGS }
                        )
                    }

                    SettingMenuState.DIAGNOSTIC_LOG -> {
                        DiagnosticLogSettingsSection(
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            neonBlue = neonBlue,
                            neonRed = neonRed,
                            neonAmber = neonAmber,
                            cardBg = cardBg
                        )
                    }
            }
        }
    }
}
}
