package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.sync.SyncState
import com.example.dinoroar.network.CheckInStatusResponse
import com.example.dinoroar.theme.LocalAppColors
import com.example.dinoroar.ui.main.MainTab

data class NetworkLineState(
    val title: String,
    val isIntranet: Boolean,
    val icon: ImageVector,
    val color: Color
)

/**
 * 首页看板顶部栏：包含菜单/后退导航、当前 Tab 标题、蛋能量徽标、独立签到按钮、网络通道与同步按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    currentTab: MainTab,
    hasFilterPersons: Boolean,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit,
    observedEggEnergy: Int,
    checkInStatus: CheckInStatusResponse?,
    isCheckingIn: Boolean,
    onCheckInClick: () -> Unit,
    onNavigateToEnergyLedger: () -> Unit,
    currentServerUrl: String,
    intranetUrl: String,
    extranetUrl: String,
    syncState: SyncState,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    var isNetworkInfoExpanded by remember { mutableStateOf(false) }

    val netState = remember(currentServerUrl, intranetUrl, extranetUrl) {
        when {
            currentServerUrl.isBlank() -> {
                NetworkLineState("未配置服务器", false, Icons.Default.CloudOff, Color.Gray)
            }
            intranetUrl.isNotBlank() && currentServerUrl.startsWith(intranetUrl) -> {
                NetworkLineState("家庭局域网 (内网)", true, Icons.Default.Router, appColors.neonGreen)
            }
            extranetUrl.isNotBlank() && currentServerUrl.startsWith(extranetUrl) -> {
                NetworkLineState("云端服务线路 (外网)", false, Icons.Default.Public, appColors.neonBlue)
            }
            else -> {
                NetworkLineState("自定义网络通道", false, Icons.Default.Dns, appColors.neonAmber)
            }
        }
    }

    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = when (currentTab) {
                    MainTab.DASHBOARD -> "首页看板"
                    MainTab.DIARY_LIST -> "我的日记"
                    MainTab.PERSONS -> "关系人管理"
                    MainTab.HANDCRAFT_SHOP -> "手账商城"
                },
                color = appColors.neonAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (currentTab == MainTab.HANDCRAFT_SHOP || (currentTab == MainTab.DIARY_LIST && hasFilterPersons)) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = appColors.neonAmber
                    )
                }
            } else {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = appColors.neonAmber
                    )
                }
            }
        },
        actions = {
            // 网络通道详情下拉
            Box {
                IconButton(onClick = { isNetworkInfoExpanded = true }) {
                    Icon(
                        imageVector = netState.icon,
                        contentDescription = "Network State Indicator",
                        tint = netState.color
                    )
                }
                DropdownMenu(
                    expanded = isNetworkInfoExpanded,
                    onDismissRequest = { isNetworkInfoExpanded = false },
                    modifier = Modifier
                        .background(appColors.cardBg)
                        .border(1.dp, appColors.neonAmber.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .width(220.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "📡 同步通道详情",
                            color = appColors.neonAmber,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                        Text(
                            text = "线路: ${netState.title}",
                            color = appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "物理地址: ${currentServerUrl.ifBlank { "未连接" }}",
                            color = appColors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                        if (netState.isIntranet) {
                            Text(
                                text = "✨ 正在享受局域网极速同步",
                                color = appColors.neonGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else if (currentServerUrl.isNotBlank() && currentServerUrl == extranetUrl) {
                            Text(
                                text = "☁️ 已切为外网云端兜底线路",
                                color = appColors.neonBlue,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // 手动同步按钮 (同步中转圈并禁用重复点击，完成后恢复稳定云朵)
            val isSyncing = syncState is SyncState.Syncing
            IconButton(
                onClick = onSyncClick,
                enabled = !isSyncing
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = appColors.neonBlue
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Sync",
                            tint = appColors.neonAmber
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.darkBg)
    )
}
