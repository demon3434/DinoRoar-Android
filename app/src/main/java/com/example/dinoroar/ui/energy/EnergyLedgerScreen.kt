package com.example.dinoroar.ui.energy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.theme.LocalAppColors
import com.example.dinoroar.ui.energy.components.*

/**
 * 银行流水级蛋能量账本全屏独立页面 (紧凑 5 维时间筛选 + 标准后端分页)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyLedgerScreen(
    apiService: DinoApiService,
    securePrefs: SecurePrefs,
    onBack: () -> Unit,
    onNavigateToDiary: ((String) -> Unit)? = null,
    onNavigateToShop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: EnergyLedgerViewModel = viewModel()
) {
    val serverBaseUrl = remember { securePrefs.serverUrl?.removeSuffix("/") ?: "" }
    val appColors = LocalAppColors.current

    LaunchedEffect(Unit) {
        viewModel.loadTransactions(apiService, isRefresh = false)
    }

    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val currentTimeRange by viewModel.currentTimeRange.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val pageSize by viewModel.pageSize.collectAsStateWithLifecycle()
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val selectedReceiptTx by viewModel.selectedReceiptTx.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.darkBg),
        containerColor = appColors.darkBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "蛋能量流水明细",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = appColors.textPrimary
                        )
                        // 顶部小巧显示当前可用余额
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = appColors.neonAmber.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, appColors.neonAmber.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "可用 ${summary.current_balance} 🥚",
                                color = appColors.neonAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = appColors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadTransactions(apiService, isRefresh = true) },
                        enabled = !isLoading && !isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = if (isRefreshing) appColors.neonGreen else appColors.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appColors.darkBg,
                    titleContentColor = appColors.textPrimary,
                    navigationIconContentColor = appColors.textPrimary
                )
            )
        },
        bottomBar = {
            // 底部标准后端分页控制条 (同 Web 端)
            if (transactions.isNotEmpty()) {
                EnergyPaginationBar(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    totalCount = totalCount,
                    pageSize = pageSize,
                    isLoading = isLoading,
                    onPageChange = { viewModel.goToPage(it, apiService) },
                    onPageSizeChange = { viewModel.setPageSize(it, apiService) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // 1. 紧凑 5 维立体时间卡片 (极少占用屏幕高度)
                item(key = "header_summary") {
                    EnergySummaryHeader(
                        summary = summary,
                        currentTimeRange = currentTimeRange,
                        onTimeRangeSelected = { viewModel.setTimeRange(it, apiService) }
                    )
                }

                // 2. 快捷收支筛选栏
                item(key = "header_filter_bar") {
                    EnergyFilterBar(
                        currentFilter = currentFilter,
                        onFilterSelected = { viewModel.setFilter(it, apiService) },
                        currentTimeRange = currentTimeRange,
                        onClearTimeRange = { viewModel.setTimeRange(LedgerTimeRange.ALL, apiService) }
                    )
                }

                // 3. 错误提示条
                if (errorMessage != null) {
                    item(key = "header_error") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = appColors.neonRed.copy(alpha = 0.10f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, appColors.neonRed.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    color = appColors.neonRed,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.loadTransactions(apiService, isRefresh = true) }) {
                                    Text(text = "重试", color = appColors.neonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 4. 空状态
                if (!isLoading && transactions.isEmpty()) {
                    item(key = "empty_state") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🥚", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = when (currentFilter) {
                                    LedgerFilterType.INCOME -> "暂无蛋能量获得记录"
                                    LedgerFilterType.EXPENSE -> "暂无蛋能量消耗记录"
                                    LedgerFilterType.ALL -> "暂无任何蛋能量流水记录"
                                },
                                color = appColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "写手账、每日敲蛋或商城兑换后将在此自动记账",
                                color = appColors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 5. 平铺连续流水卡片列表
                items(transactions, key = { it.id }) { tx ->
                    EnergyTransactionCard(
                        tx = tx,
                        serverBaseUrl = serverBaseUrl,
                        onClick = { viewModel.openReceipt(tx) }
                    )
                }
            }

            // 正在加载浮层
            if (isLoading && transactions.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = appColors.neonGreen
                )
            }

            // 拟物电子回单弹窗
            selectedReceiptTx?.let { tx ->
                EnergyReceiptDialog(
                    tx = tx,
                    serverBaseUrl = serverBaseUrl,
                    onDismiss = { viewModel.closeReceipt() },
                    onNavigateToDiary = onNavigateToDiary,
                    onNavigateToShop = onNavigateToShop
                )
            }
        }
    }
}
