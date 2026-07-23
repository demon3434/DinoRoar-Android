package com.example.dinoroar.ui.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.data.sync.SyncManager
import com.example.dinoroar.data.sync.SyncState
import com.example.dinoroar.network.ConnectionManager
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.theme.LocalAppColors
import com.example.dinoroar.ui.main.components.*
import kotlinx.coroutines.launch
import com.example.dinoroar.ui.sticker.StickerExchangeScreen

enum class MainTab {
    DASHBOARD,    // 首页看板
    DIARY_LIST,   // 我的日记
    PERSONS,      // 关系人管理
    STICKER_SHOP  // 贴纸商店
}

private data class NetworkLineState(
    val title: String,
    val isIntranet: Boolean,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: DataRepository,
    syncManager: SyncManager,
    securePrefs: SecurePrefs,
    apiService: DinoApiService,
    connectionManager: ConnectionManager,
    onNavigateToCreate: (String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToPersonCategoryManage: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        MainScreenViewModel(repository)
    }
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // 从 ViewModel 中订阅所有需要的数据流
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val allAttachments by viewModel.allAttachments.collectAsStateWithLifecycle()
    val allCrossRefs by viewModel.allCrossRefs.collectAsStateWithLifecycle()
    val allPersons by viewModel.allPersons.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val logPersonMap by viewModel.logPersonMap.collectAsStateWithLifecycle()
    val filteredLogs by viewModel.filteredLogs.collectAsStateWithLifecycle()
    val availableMonths by viewModel.availableMonths.collectAsStateWithLifecycle()
    val sortedPersonsForFilter by viewModel.sortedPersonsForFilter.collectAsStateWithLifecycle()
    val energyDelta by viewModel.energyDeltaSummary.collectAsStateWithLifecycle()
    val reviewSummary by viewModel.dashboardReviewSummary.collectAsStateWithLifecycle()
    val categorySummaries by viewModel.categoryPersonsSummary.collectAsStateWithLifecycle()
    
    val selectedFilterPersonUuids by viewModel.selectedFilterPersonUuids.collectAsStateWithLifecycle()
    val selectedFilterMonths by viewModel.selectedFilterMonths.collectAsStateWithLifecycle()
    val selectedFilterMoods by viewModel.selectedFilterMoods.collectAsStateWithLifecycle()

    val syncState by syncManager.syncState.collectAsStateWithLifecycle()
    val allDinoConfigs by repository.getAllActiveDinoConfigsFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    // 触发启动同步
    LaunchedEffect(Unit) {
        syncManager.sync()
    }

    val appColors = LocalAppColors.current
    val darkBg = appColors.darkBg
    val neonBlue = appColors.neonBlue
    val neonGreen = appColors.neonGreen
    val neonAmber = appColors.neonAmber
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary
    val cardBg = appColors.cardBg
    val neonRed = appColors.neonRed

    val observedEggEnergy by produceState(initialValue = securePrefs.eggEnergy) {
        while (true) {
            value = securePrefs.eggEnergy
            kotlinx.coroutines.delay(500)
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()

    val selectedFilterPersonsForBack by viewModel.selectedFilterPersonUuids.collectAsStateWithLifecycle()

    BackHandler(enabled = currentTab != MainTab.DASHBOARD) {
        if (currentTab == MainTab.DIARY_LIST && selectedFilterPersonsForBack.isNotEmpty()) {
            viewModel.clearFilter()
        }
        currentTab = MainTab.DASHBOARD
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = darkBg,
                modifier = Modifier.width(280.dp).fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Column(
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 12.dp)
                        ) {
                            val nickName = securePrefs.nickname.ifBlank { "勇敢小润" }
                            Text(
                                text = "🦖 $nickName 🦕",
                                color = neonAmber,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🥚 蛋能量: $observedEggEnergy",
                                color = neonGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }

                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        NavigationDrawerItem(
                            label = { Text("🏠 首页看板", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                            selected = currentTab == MainTab.DASHBOARD,
                            onClick = {
                                currentTab = MainTab.DASHBOARD
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = neonGreen.copy(alpha = 0.15f),
                                unselectedContainerColor = Color.Transparent,
                                selectedTextColor = neonGreen,
                                unselectedTextColor = textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NavigationDrawerItem(
                            label = { Text("📓 我的日记", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                            selected = currentTab == MainTab.DIARY_LIST,
                            onClick = {
                                currentTab = MainTab.DIARY_LIST
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = neonBlue.copy(alpha = 0.15f),
                                unselectedContainerColor = Color.Transparent,
                                selectedTextColor = neonBlue,
                                unselectedTextColor = textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NavigationDrawerItem(
                            label = { Text("👥 关系人管理", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                            selected = currentTab == MainTab.PERSONS,
                            onClick = {
                                currentTab = MainTab.PERSONS
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = neonGreen.copy(alpha = 0.15f),
                                unselectedContainerColor = Color.Transparent,
                                selectedTextColor = neonGreen,
                                unselectedTextColor = textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NavigationDrawerItem(
                            label = { Text("🎨 贴纸商店", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                            selected = currentTab == MainTab.STICKER_SHOP,
                            onClick = {
                                currentTab = MainTab.STICKER_SHOP
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = neonGreen.copy(alpha = 0.15f),
                                unselectedContainerColor = Color.Transparent,
                                selectedTextColor = neonGreen,
                                unselectedTextColor = textSecondary
                            )
                        )
                    }

                    Column {
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        NavigationDrawerItem(
                            label = { Text("⚙️ 系统设置", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.close()
                                    onNavigateToSettings()
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                                unselectedTextColor = textPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NavigationDrawerItem(
                            label = { Text("🚪 退出登录", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.close()
                                    onLogout()
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                                unselectedTextColor = neonRed
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when(currentTab) {
                                MainTab.DASHBOARD -> "首页看板"
                                MainTab.DIARY_LIST -> "我的日记"
                                MainTab.PERSONS -> "关系人管理"
                                MainTab.STICKER_SHOP -> "贴纸商店"
                            },
                            color = neonAmber,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        val selectedFilterPersons by viewModel.selectedFilterPersonUuids.collectAsStateWithLifecycle()

                        if (currentTab == MainTab.STICKER_SHOP) {
                            IconButton(onClick = { currentTab = MainTab.DASHBOARD }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = neonAmber
                                )
                            }
                        } else if (currentTab == MainTab.DIARY_LIST && selectedFilterPersons.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.clearFilter()
                                currentTab = MainTab.DASHBOARD
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = neonAmber
                                )
                            }
                        } else {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = neonAmber
                                )
                            }
                        }
                    },
                    actions = {
                        var isNetworkInfoExpanded by remember { mutableStateOf(false) }
                        val currentServerUrl = securePrefs.serverUrl ?: ""
                        val intranetUrl = securePrefs.intranetUrl ?: ""
                        val extranetUrl = securePrefs.extranetUrl ?: ""

                        val netState = when {
                            currentServerUrl.isBlank() -> {
                                NetworkLineState("未配置服务器", false, Icons.Default.CloudOff, Color.Gray)
                            }
                            intranetUrl.isNotBlank() && currentServerUrl.startsWith(intranetUrl) -> {
                                NetworkLineState("家庭局域网 (内网)", true, Icons.Default.Router, neonGreen)
                            }
                            extranetUrl.isNotBlank() && currentServerUrl.startsWith(extranetUrl) -> {
                                NetworkLineState("云端服务线路 (外网)", false, Icons.Default.Public, neonBlue)
                            }
                            else -> {
                                NetworkLineState("自定义网络通道", false, Icons.Default.Dns, neonAmber)
                            }
                        }

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
                                    .background(cardBg)
                                    .border(1.dp, neonAmber.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                                    .width(220.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "📡 同步通道详情",
                                        color = neonAmber,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                                    Text(
                                        text = "线路: ${netState.title}",
                                        color = textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    Text(
                                        text = "物理地址: ${currentServerUrl.ifBlank { "未连接" }}",
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        lineHeight = 15.sp
                                    )
                                    if (netState.isIntranet) {
                                        Text(
                                            text = "✨ 正在享受局域网极速同步",
                                            color = neonGreen,
                                            fontSize = 11.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    } else if (currentServerUrl.isNotBlank() && currentServerUrl == extranetUrl) {
                                        Text(
                                            text = "☁️ 已切为外网云端兜底线路",
                                            color = neonBlue,
                                            fontSize = 11.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val result = syncManager.sync(isManual = true)
                                    if (result is SyncState.Success) {
                                        Toast.makeText(context, "同步成功！", Toast.LENGTH_SHORT).show()
                                    } else if (result is SyncState.Error) {
                                        Toast.makeText(context, "同步失败: ${result.error}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = "Sync",
                                tint = if (syncState is SyncState.Syncing) neonBlue else neonAmber
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
                )
            },
            floatingActionButton = {
                if (currentTab != MainTab.STICKER_SHOP) {
                    FloatingActionButton(
                        onClick = { onNavigateToCreate(null) },
                        containerColor = neonAmber,
                        contentColor = Color.Black
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Diary")
                    }
                }
            },
            containerColor = darkBg,
            modifier = modifier
        ) { innerPadding ->
            when (currentTab) {
                MainTab.DASHBOARD -> {
                    DashboardTab(
                        logs = logs,
                        allAttachments = allAttachments,
                        allPersons = allPersons,
                        allCrossRefs = allCrossRefs,
                        allCategories = allCategories,
                        securePrefs = securePrefs,
                        eggEnergy = observedEggEnergy,
                        energyDelta = energyDelta,
                        reviewSummary = reviewSummary,
                        categorySummaries = categorySummaries,
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToCreate = onNavigateToCreate,
                        onFilterPerson = { personUuid ->
                            viewModel.applyFilter(setOf(personUuid), emptySet(), emptySet())
                            currentTab = MainTab.DIARY_LIST
                        },
                        onNavigateToPersonManage = { currentTab = MainTab.PERSONS },
                        innerPadding = innerPadding
                    )
                }
                MainTab.DIARY_LIST -> {
                    DiaryListTab(
                        filteredLogs = filteredLogs,
                        logPersonMap = logPersonMap,
                        allPersons = allPersons,
                        allCategories = allCategories,
                        allDinoConfigs = allDinoConfigs,
                        availableMonths = availableMonths,
                        selectedFilterPersonUuids = selectedFilterPersonUuids,
                        selectedFilterMonths = selectedFilterMonths,
                        selectedFilterMoods = selectedFilterMoods,
                        sortedPersonsForFilter = sortedPersonsForFilter,
                        onSearchQueryChange = { query ->
                            viewModel.updateSearchQuery(query)
                        },
                        onConfirmFilter = { personUuids, months, moods ->
                            viewModel.applyFilter(personUuids, months, moods)
                        },
                        onClearFilter = {
                            viewModel.clearFilter()
                        },
                        onRemovePersonFilter = { personUuid ->
                            viewModel.applyFilter(selectedFilterPersonUuids - personUuid, selectedFilterMonths, selectedFilterMoods)
                        },
                        onRemoveMonthFilter = { month ->
                            viewModel.applyFilter(selectedFilterPersonUuids, selectedFilterMonths - month, selectedFilterMoods)
                        },
                        onRemoveMoodFilter = { moodId ->
                            viewModel.applyFilter(selectedFilterPersonUuids, selectedFilterMonths, selectedFilterMoods - moodId)
                        },
                        onDeleteLog = { logUuid ->
                            coroutineScope.launch {
                                repository.softDeleteLog(logUuid)
                                Toast.makeText(context, "日记已删除，将在下次同步时上报", Toast.LENGTH_SHORT).show()
                                syncManager.sync()
                            }
                        },
                        onNavigateToCreate = onNavigateToCreate,
                        onNavigateToDetail = onNavigateToDetail,
                        repository = repository,
                        syncManager = syncManager,
                        listState = listState,
                        innerPadding = innerPadding
                    )
                }
                MainTab.PERSONS -> {
                    PersonsTab(
                        allPersons = allPersons,
                        allCategories = allCategories,
                        repository = repository,
                        onNavigateToPersonCategoryManage = onNavigateToPersonCategoryManage,
                        innerPadding = innerPadding
                    )
                }
                MainTab.STICKER_SHOP -> {
                    StickerExchangeScreen(
                        apiService = apiService,
                        securePrefs = securePrefs,
                        onBack = { currentTab = MainTab.DASHBOARD },
                        showTopAppBar = false,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
