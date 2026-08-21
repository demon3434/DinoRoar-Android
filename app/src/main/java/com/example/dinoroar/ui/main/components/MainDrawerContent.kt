package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.network.CheckInStatusResponse
import com.example.dinoroar.theme.LocalAppColors
import com.example.dinoroar.ui.main.MainTab

/**
 * 侧滑抽屉菜单内容区：包含昵称、蛋能量、手动签到按钮、页面导航项与系统设置/退出
 */
@Composable
fun MainDrawerContent(
    nickName: String,
    observedEggEnergy: Int,
    checkInStatus: CheckInStatusResponse?,
    isCheckingIn: Boolean,
    onCheckInClick: () -> Unit,
    onNavigateToEnergyLedger: () -> Unit,
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    var showCheckInRuleDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // 顶部用户信息卡片 (昵称 + 蛋能量 + 签到按钮)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = "🦖 $nickName 🦕",
                    color = appColors.neonAmber,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 蛋能量 (左) 与 签到按钮 + 规则问号 (右) 横向排布
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onNavigateToEnergyLedger() }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "🥚 蛋能量: $observedEggEnergy",
                            color = appColors.neonGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val hasCheckedIn = checkInStatus?.has_checked_in_today == true
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (hasCheckedIn) appColors.neonGreen.copy(alpha = 0.12f) else appColors.neonGreen,
                            border = BorderStroke(1.dp, if (hasCheckedIn) appColors.neonGreen.copy(alpha = 0.35f) else appColors.neonGreen),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !hasCheckedIn && !isCheckingIn) {
                                    onCheckInClick()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isCheckingIn) "⏳ 签到中" else if (hasCheckedIn) "✅ 已签" else "🥚 签到",
                                    color = if (hasCheckedIn) appColors.neonGreen else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 签到规则说明小问号
                        IconButton(
                            onClick = { showCheckInRuleDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "签到规则",
                                tint = appColors.textSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // 导航菜单项
            NavigationDrawerItem(
                label = { Text("🏠 首页看板", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                selected = currentTab == MainTab.DASHBOARD,
                onClick = { onTabSelected(MainTab.DASHBOARD) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = appColors.neonGreen.copy(alpha = 0.15f),
                    unselectedContainerColor = Color.Transparent,
                    selectedTextColor = appColors.neonGreen,
                    unselectedTextColor = appColors.textSecondary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("📓 我的日记", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                selected = currentTab == MainTab.DIARY_LIST,
                onClick = { onTabSelected(MainTab.DIARY_LIST) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = appColors.neonBlue.copy(alpha = 0.15f),
                    unselectedContainerColor = Color.Transparent,
                    selectedTextColor = appColors.neonBlue,
                    unselectedTextColor = appColors.textSecondary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("👥 关系人管理", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                selected = currentTab == MainTab.PERSONS,
                onClick = { onTabSelected(MainTab.PERSONS) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = appColors.neonGreen.copy(alpha = 0.15f),
                    unselectedContainerColor = Color.Transparent,
                    selectedTextColor = appColors.neonGreen,
                    unselectedTextColor = appColors.textSecondary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("🛍️ 手账商城", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                selected = currentTab == MainTab.HANDCRAFT_SHOP,
                onClick = { onTabSelected(MainTab.HANDCRAFT_SHOP) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = appColors.neonGreen.copy(alpha = 0.15f),
                    unselectedContainerColor = Color.Transparent,
                    selectedTextColor = appColors.neonGreen,
                    unselectedTextColor = appColors.textSecondary
                )
            )
        }

        // 底部系统设置与退出登录
        Column {
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            NavigationDrawerItem(
                label = { Text("⚙️ 系统设置", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                selected = false,
                onClick = onNavigateToSettings,
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent,
                    unselectedTextColor = appColors.textPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("🚪 退出登录", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                selected = false,
                onClick = onLogout,
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent,
                    unselectedTextColor = appColors.neonRed
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showCheckInRuleDialog) {
            CheckInRuleDialog(
                onDismiss = { showCheckInRuleDialog = false }
            )
        }
    }
}

