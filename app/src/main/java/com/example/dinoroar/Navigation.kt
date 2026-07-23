package com.example.dinoroar

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.data.local.ActivityStateTracker
import com.example.dinoroar.data.sync.SyncManager
import com.example.dinoroar.media.AudioRecorder
import com.example.dinoroar.media.MediaCompressor
import com.example.dinoroar.network.ConnectionManager
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.network.NsdHelper
import com.example.dinoroar.ui.auth.LoginScreen
import com.example.dinoroar.ui.connection.SetupConnectionScreen
import com.example.dinoroar.ui.diary.LogCreateScreen
import com.example.dinoroar.ui.diary.LogDetailScreen
import com.example.dinoroar.ui.lock.CamouflageGameScreen
import com.example.dinoroar.ui.lock.NineGridLockScreen
import com.example.dinoroar.ui.main.MainScreen
import com.example.dinoroar.ui.settings.SettingsScreen
import com.example.dinoroar.ui.settings.SettingsEditPatternScreen
import com.example.dinoroar.ui.person.PersonSelectScreen
import com.example.dinoroar.ui.person.PersonCategoryManageScreen
import com.example.dinoroar.ui.person.PersonEditScreen
import com.example.dinoroar.ui.person.CategorySelectScreen

object CategorySelectorState {
    var onCategorySelected: ((String?, String) -> Unit)? = null
}

object PersonSelectorState {
    var onPersonsSelected: ((List<String>) -> Unit)? = null
}

@Composable
fun MainNavigation(
    nsdHelper: NsdHelper,
    connectionManager: ConnectionManager,
    securePrefs: SecurePrefs,
    apiService: DinoApiService,
    repository: DataRepository,
    syncManager: SyncManager,
    audioRecorder: AudioRecorder,
    mediaCompressor: MediaCompressor,
    onThemeChanged: (Int) -> Unit
) {
    // Resolve start destination
    // Resolve start destination
    val startDestination = when {
        securePrefs.token == null -> Login
        securePrefs.isCamouflageEnabled -> CamouflageGame
        else -> NineGridLock
    }

    val backStack = rememberNavBackStack(startDestination)
    var prevBackStackList by remember { mutableStateOf(backStack.toList()) }

    val context = LocalContext.current
    LaunchedEffect(backStack.lastOrNull()) {
        prevBackStackList = backStack.toList()
        val activity = context as? Activity ?: return@LaunchedEffect
        val currentKey = backStack.lastOrNull()
        if (currentKey == CamouflageGame) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (ActivityStateTracker.isCameraActive || ActivityStateTracker.isExternalActivityActive) {
                    return@LifecycleEventObserver
                }
                if (securePrefs.token != null) {
                    if (backStack.isNotEmpty() && 
                        backStack.last() != CamouflageGame && 
                        backStack.last() != NineGridLock) {
                        if (securePrefs.isCamouflageEnabled) {
                            backStack.add(CamouflageGame)
                        } else {
                            backStack.add(NineGridLock)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 拦截手机的物理返回按键与手势侧滑返回，保护伪装游戏、锁屏及栈底页面防止绕过泄露日记内容
    val activity = context as? Activity
    BackHandler(enabled = true) {
        val last = backStack.lastOrNull()
        if (last == CamouflageGame || last == NineGridLock || last == Login || backStack.toList().size <= 1) {
            activity?.finish()
        } else {
            backStack.removeLastOrNull()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            val isPop = run {
                val initialKey = initialState.key
                val targetKey = targetState.key
                val stackList = backStack.toList()
                
                fun Any.extractKey(): Any {
                    return try {
                        val keyField = this.javaClass.declaredFields.firstOrNull { it.name == "key" }
                        keyField?.isAccessible = true
                        keyField?.get(this) ?: this
                    } catch (e: Exception) {
                        this
                    }
                }
                
                val initialInStack = stackList.indexOfFirst { it.extractKey() == initialKey }
                val targetInStack = stackList.indexOfFirst { it.extractKey() == targetKey }
                if (initialInStack == -1) {
                    true
                } else if (targetInStack != -1) {
                    targetInStack < initialInStack
                } else {
                    false
                }
            }
            
            if (isPop) {
                androidx.compose.animation.ContentTransform(
                    targetContentEnter = androidx.compose.animation.slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { -it },
                    initialContentExit = androidx.compose.animation.slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { it },
                    sizeTransform = androidx.compose.animation.SizeTransform { _, _ -> androidx.compose.animation.core.snap() }
                )
            } else {
                androidx.compose.animation.ContentTransform(
                    targetContentEnter = androidx.compose.animation.slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { it },
                    initialContentExit = androidx.compose.animation.slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { -it },
                    sizeTransform = androidx.compose.animation.SizeTransform { _, _ -> androidx.compose.animation.core.snap() }
                )
            }
        },
        entryProvider = entryProvider {
            
            entry<CamouflageGame> {
                CamouflageGameScreen(
                    onTriggerUnlock = {
                        backStack.add(NineGridLock)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<NineGridLock> {
                NineGridLockScreen(
                    correctPattern = securePrefs.lockPattern,
                    onUnlockSuccess = {
                        backStack.removeLastOrNull()
                        if (backStack.isNotEmpty() && backStack.last() == CamouflageGame) {
                            backStack.removeLastOrNull()
                        }
                        if (backStack.isEmpty()) {
                            backStack.add(Main)
                        }
                    },
                    onBackToGame = {
                        if (securePrefs.isCamouflageEnabled) {
                            backStack.removeLastOrNull()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<Login> {
                LoginScreen(
                    nsdHelper = nsdHelper,
                    connectionManager = connectionManager,
                    apiService = apiService,
                    securePrefs = securePrefs,
                    onLoginSuccess = {
                        backStack.removeLastOrNull() // 弹出并销毁当前登录页，以防后续返回栈循环
                        if (securePrefs.isCamouflageEnabled) {
                            backStack.add(CamouflageGame)
                        } else {
                            backStack.add(Main)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<Main> {
                MainScreen(
                    repository = repository,
                    syncManager = syncManager,
                    securePrefs = securePrefs,
                    apiService = apiService,
                    connectionManager = connectionManager,
                    onNavigateToCreate = { editUuid ->
                        backStack.add(LogCreate(editingLogUuid = editUuid))
                    },
                    onNavigateToSettings = {
                        backStack.add(Settings)
                    },
                    onNavigateToDetail = { detailUuid ->
                        backStack.add(LogDetail(logUuid = detailUuid))
                    },
                    onNavigateToPersonCategoryManage = {
                        backStack.add(PersonCategoryManage)
                    },
                    onLogout = {
                        securePrefs.token = null
                        backStack.clear()
                        backStack.add(Login)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<LogDetail> { key ->
                LogDetailScreen(
                    repository = repository,
                    syncManager = syncManager,
                    apiService = apiService,
                    securePrefs = securePrefs,
                    logUuid = key.logUuid,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    onNavigateToEdit = { editUuid ->
                        backStack.add(LogCreate(editingLogUuid = editUuid))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<LogCreate> { key ->
                LogCreateScreen(
                    repository = repository,
                    syncManager = syncManager,
                    audioRecorder = audioRecorder,
                    mediaCompressor = mediaCompressor,
                    apiService = apiService,
                    editingLogUuid = key.editingLogUuid,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    onNavigateToPersonSelect = { currentSelected ->
                        backStack.add(PersonSelect(selectedUuids = currentSelected))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<Settings> {
                SettingsScreen(
                    nsdHelper = nsdHelper,
                    securePrefs = securePrefs,
                    repository = repository,
                    onThemeChanged = onThemeChanged,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    onNavigateToEditPattern = {
                        backStack.add(SettingsEditPattern)
                    },
                    onLogout = {
                        securePrefs.token = null
                        backStack.add(Login)
                    },
                    onDisconnect = {
                        securePrefs.serverUrl = null
                        securePrefs.token = null
                        backStack.add(Login)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<SettingsEditPattern> {
                SettingsEditPatternScreen(
                    securePrefs = securePrefs,
                    apiService = apiService,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<PersonSelect> { key ->
                PersonSelectScreen(
                    repository = repository,
                    selectedUuids = key.selectedUuids,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    onNavigateToPersonEdit = { uuid, isTemp, defaultCat ->
                        backStack.add(PersonEdit(uuid, isTemp, defaultCat))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<PersonCategoryManage> {
                PersonCategoryManageScreen(
                    repository = repository,
                    syncManager = syncManager,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    onNavigateToPersonEdit = { uuid, isTemp, defaultCat ->
                        backStack.add(PersonEdit(uuid, isTemp, defaultCat))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<PersonEdit> { key ->
                PersonEditScreen(
                    personUuid = key.personUuid,
                    isTemp = key.isTemp,
                    defaultCategoryUuid = key.defaultCategoryUuid,
                    repository = repository,
                    syncManager = syncManager,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    onNavigateToCategorySelect = { currentCatUuid ->
                        backStack.add(CategorySelect(currentCatUuid))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<CategorySelect> { key ->
                CategorySelectScreen(
                    currentCategoryUuid = key.currentCategoryUuid,
                    repository = repository,
                    syncManager = syncManager,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}
