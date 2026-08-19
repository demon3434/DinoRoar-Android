package com.example.dinoroar.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.data.sync.SyncManager
import com.example.dinoroar.data.sync.SyncState
import com.example.dinoroar.network.ConnectionManager
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.theme.LocalAppColors
import com.example.dinoroar.ui.main.components.*
import kotlinx.coroutines.launch

enum class MainTab {
    DASHBOARD,      // 首页看板
    DIARY_LIST,     // 我的日记
    PERSONS,        // 关系人管理
    HANDCRAFT_SHOP  // 手账商城
}

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
    onNavigateToEnergyLedger: () -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        MainScreenViewModel(repository)
    }
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val appColors = LocalAppColors.current

    // 从 ViewModel 中订阅数据流
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

    val checkInStatus by viewModel.checkInStatus.collectAsStateWithLifecycle()
    val isCheckingIn by viewModel.isCheckingIn.collectAsStateWithLifecycle()

    var bubbleState by remember { mutableStateOf(CheckInBubbleState()) }

    fun showBubble(icon: String, title: String, message: String, isCrit: Boolean = false, isSuccess: Boolean = true) {
        bubbleState = CheckInBubbleState(
            isVisible = true,
            icon = icon,
            title = title,
            message = message,
            isCrit = isCrit,
            isSuccess = isSuccess
        )
    }

    fun handleCheckInAction() {
        if (checkInStatus?.has_checked_in_today == true) {
            showBubble("ℹ️", "今日已签到", "您今天已经敲过蛋啦，明天继续加油哦！", isCrit = false, isSuccess = true)
            return
        }
        if (isCheckingIn) return

        viewModel.performCheckIn(
            apiService = apiService,
            securePrefs = securePrefs,
            onSuccess = { res ->
                if (res.already_checked_in) {
                    showBubble("ℹ️", "今日已签到", res.message.ifBlank { "今日已经完成敲蛋签到啦！" }, isCrit = false, isSuccess = true)
                } else {
                    val critText = if (res.is_crit) " 💥 触发欧皇暴击！" else ""
                    showBubble(
                        icon = "🎉",
                        title = "敲蛋签到成功！$critText",
                        message = "连续签到第 ${res.streak_days} 天 · 获得 +${res.total_reward} 蛋能量！",
                        isCrit = res.is_crit,
                        isSuccess = true
                    )
                }
            },
            onError = { err ->
                showBubble("❌", "签到失败", err, isCrit = false, isSuccess = false)
            }
        )
    }

    val syncState by syncManager.syncState.collectAsStateWithLifecycle()
    val allDinoConfigs by repository.getAllActiveDinoConfigsFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    // 触发启动同步与签到状态刷新 (仅拉取状态，不自动签到)
    LaunchedEffect(Unit) {
        syncManager.sync()
        viewModel.fetchCheckInStatus(apiService)
    }

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
                drawerContainerColor = appColors.darkBg,
                modifier = Modifier.width(280.dp).fillMaxHeight()
            ) {
                MainDrawerContent(
                    nickName = securePrefs.nickname.ifBlank { "勇敢小润" },
                    observedEggEnergy = observedEggEnergy,
                    checkInStatus = checkInStatus,
                    isCheckingIn = isCheckingIn,
                    onCheckInClick = { handleCheckInAction() },
                    onNavigateToEnergyLedger = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToEnergyLedger()
                    },
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        currentTab = tab
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNavigateToSettings = {
                        coroutineScope.launch {
                            drawerState.close()
                            onNavigateToSettings()
                        }
                    },
                    onLogout = {
                        coroutineScope.launch {
                            drawerState.close()
                            onLogout()
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                MainTopBar(
                    currentTab = currentTab,
                    hasFilterPersons = selectedFilterPersonsForBack.isNotEmpty(),
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                    onBackClick = {
                        if (currentTab == MainTab.HANDCRAFT_SHOP) {
                            currentTab = MainTab.DASHBOARD
                        } else if (currentTab == MainTab.DIARY_LIST && selectedFilterPersonsForBack.isNotEmpty()) {
                            viewModel.clearFilter()
                            currentTab = MainTab.DASHBOARD
                        }
                    },
                    observedEggEnergy = observedEggEnergy,
                    checkInStatus = checkInStatus,
                    isCheckingIn = isCheckingIn,
                    onCheckInClick = { handleCheckInAction() },
                    onNavigateToEnergyLedger = onNavigateToEnergyLedger,
                    currentServerUrl = securePrefs.serverUrl ?: "",
                    intranetUrl = securePrefs.intranetUrl ?: "",
                    extranetUrl = securePrefs.extranetUrl ?: "",
                    syncState = syncState,
                    onSyncClick = {
                        coroutineScope.launch {
                            val result = syncManager.sync(isManual = true)
                            if (result is SyncState.Success) {
                                Toast.makeText(context, "同步成功！", Toast.LENGTH_SHORT).show()
                            } else if (result is SyncState.Error) {
                                Toast.makeText(context, "同步失败: ${result.error}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (currentTab != MainTab.HANDCRAFT_SHOP) {
                    FloatingActionButton(
                        onClick = { onNavigateToCreate(null) },
                        containerColor = appColors.neonAmber,
                        contentColor = Color.Black
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Diary")
                    }
                }
            },
            containerColor = appColors.darkBg
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
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
                            apiService = apiService,
                            onNavigateToDetail = onNavigateToDetail,
                            onNavigateToCreate = onNavigateToCreate,
                            onFilterPerson = { personUuid ->
                                viewModel.applyFilter(setOf(personUuid), emptySet(), emptySet())
                                currentTab = MainTab.DIARY_LIST
                            },
                            onNavigateToPersonManage = onNavigateToPersonCategoryManage,
                            onNavigateToHandcraftShop = { currentTab = MainTab.HANDCRAFT_SHOP },
                            checkInStatus = checkInStatus,
                            onOpenCheckInDialog = { handleCheckInAction() },
                            onOpenEnergyHistory = onNavigateToEnergyLedger,
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
                            onSearchQueryChange = { q -> viewModel.searchQuery.value = q },
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
                    MainTab.HANDCRAFT_SHOP -> {
                        HandcraftShopMenuScreen(
                            apiService = apiService,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }

                // 每日签到敲蛋结果顶层提示弹窗 (Topmost Check-in Dialog)
                CheckInBubble(
                    state = bubbleState,
                    onDismiss = { bubbleState = bubbleState.copy(isVisible = false) }
                )
            }
        }
    }
}
