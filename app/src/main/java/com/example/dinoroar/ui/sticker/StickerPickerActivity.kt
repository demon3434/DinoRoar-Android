package com.example.dinoroar.ui.sticker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.network.StickerExchangeRequest
import com.example.dinoroar.network.StickerSeriesDto
import com.example.dinoroar.network.StickerConfigDto
import com.example.dinoroar.theme.DinoRoarTheme
import com.example.dinoroar.theme.LocalAppColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StickerPickerActivity : ComponentActivity() {

    @Inject
    lateinit var apiService: DinoApiService

    @Inject
    lateinit var securePrefs: SecurePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.dinoroar.data.local.ActivityStateTracker.onExternalActivityStarted()
        enableEdgeToEdge()

        setContent {
            var themeId by remember { mutableStateOf(securePrefs.currentThemeId) }
            DinoRoarTheme(themeId = themeId) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.example.dinoroar.ui.lock.DinoLockWrapper(securePrefs = securePrefs) {
                        StickerPickerScreen(
                            apiService = apiService,
                            securePrefs = securePrefs,
                            onCancel = { finish() },
                            onDone = { selectedIds ->
                                val intent = Intent().apply {
                                    putStringArrayListExtra("selected_sticker_ids", ArrayList(selectedIds))
                                }
                                setResult(Activity.RESULT_OK, intent)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.dinoroar.data.local.ActivityStateTracker.onExternalActivityDestroyed()
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerScreen(
    apiService: DinoApiService,
    securePrefs: SecurePrefs,
    onCancel: () -> Unit,
    onDone: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var eggEnergy by remember { mutableIntStateOf(securePrefs.eggEnergy) }
    var userInventory by remember { mutableStateOf(securePrefs.stickerInventory) }
    var seriesList by remember { mutableStateOf<List<StickerSeriesDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 选中的贴纸临时队列 (ID 列表)，上限为 10 张
    val selectedStickers = remember { mutableStateListOf<StickerConfigDto>() }

    // 联动定位组件状态
    val leftListState = rememberLazyListState()
    val rightListState = rememberLazyListState()

    // 兑换弹窗状态
    var showExchangeDialog by remember { mutableStateOf<StickerConfigDto?>(null) }
    var isExchanging by remember { mutableStateOf(false) }

    val serverBaseUrl = remember { securePrefs.serverUrl?.removeSuffix("/") ?: "" }

    // 贴纸的总原始库存量 (在本地随着多次点击扣减以实现连续选择防超限)
    val baseInventoryMap = remember { mutableStateMapOf<Int, Int>() }

    // 当 userInventory 改变时，重构原始库存 Map
    LaunchedEffect(userInventory) {
        baseInventoryMap.clear()
        userInventory.split(",").filter { it.isNotBlank() }.forEach { item ->
            val parts = item.split(":")
            if (parts.size == 2) {
                val id = parts[0].trim().toIntOrNull()
                val count = parts[1].trim().toIntOrNull()
                if (id != null && count != null) {
                    baseInventoryMap[id] = count
                }
            }
        }
    }

    // 当前剩余可点选库存量 = 原始库存 - 已点选在底栏的数目
    // 直接从 userInventory 字符串重新解析，避免 LaunchedEffect 异步时序竞态导致库存为空
    val remainingInventory = remember(userInventory, selectedStickers.size) {
        val map = mutableMapOf<Int, Int>()
        userInventory.split(",").filter { it.isNotBlank() }.forEach { item ->
            val parts = item.split(":")
            if (parts.size == 2) {
                val id = parts[0].trim().toIntOrNull()
                val count = parts[1].trim().toIntOrNull()
                if (id != null && count != null) {
                    map[id] = count
                }
            }
        }
        selectedStickers.forEach { st ->
            val count = map[st.id] ?: 0
            if (count > 0) {
                map[st.id] = count - 1
            }
        }
        map
    }

    // 动态拉取配置
    val loadConfig = {
        coroutineScope.launch {
            isLoading = true
            try {
                val serverAsset = apiService.getStickerInventory()
                securePrefs.stickerInventory = serverAsset.sticker_inventory
                securePrefs.eggEnergy = serverAsset.egg_energy
                securePrefs.lastSyncedInventory = serverAsset.sticker_inventory
                eggEnergy = serverAsset.egg_energy
                userInventory = serverAsset.sticker_inventory

                val list = apiService.getStickersConfig()
                seriesList = list.filter { it.is_active && !it.is_deleted }
                val cacheStr = list.flatMap { it.stickers }.joinToString(",") { "${it.id}:${it.image_url}" }
                securePrefs.stickerConfigCache = cacheStr

                val allStickers = list.flatMap { it.stickers }
                val activity = context as? android.app.Activity
                val alreadyUsedIds: List<String> = activity?.intent?.getStringArrayListExtra("already_used_sticker_ids") ?: emptyList()
                if (selectedStickers.isEmpty()) {
                    alreadyUsedIds.forEach { usedIdStr ->
                        val st = allStickers.find { it.id.toString() == usedIdStr || it.image_url == usedIdStr }
                        if (st != null) {
                            selectedStickers.add(st)
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "加载数据失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    val shopLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        loadConfig()
    }

    LaunchedEffect(Unit) {
        loadConfig()
    }

    val appColors = LocalAppColors.current
    val darkBg = appColors.darkBg
    val cardBg = appColors.cardBg
    val neonBlue = appColors.neonBlue
    val neonGreen = appColors.neonGreen
    val neonRed = appColors.neonRed
    val neonAmber = appColors.neonAmber
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary

    var onlyOwned by remember { mutableStateOf(true) }

    val visibleSeriesList = remember(seriesList, remainingInventory, onlyOwned) {
        if (onlyOwned) {
            seriesList.filter { series ->
                series.stickers.any { it.is_active && !it.is_deleted && (remainingInventory[it.id] ?: 0) > 0 }
            }
        } else {
            seriesList
        }
    }


    // 联动控制
    val firstVisibleItemIndex by remember {
        derivedStateOf { rightListState.firstVisibleItemIndex }
    }

    LaunchedEffect(firstVisibleItemIndex) {
        if (visibleSeriesList.isNotEmpty() && firstVisibleItemIndex in visibleSeriesList.indices) {
            leftListState.animateScrollToItem(firstVisibleItemIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🎨 我的贴纸",
                            color = neonAmber,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, neonAmber.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "🥚 $eggEnergy",
                                    color = neonAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (isLoading) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    CircularProgressIndicator(modifier = Modifier.size(8.dp), strokeWidth = 1.dp, color = neonAmber)
                                }
                            }
                        }
                    }
                },

                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = neonAmber)
                    }
                },
                actions = {
                    FilterChip(
                        selected = onlyOwned,
                        onClick = { onlyOwned = !onlyOwned },
                        label = {
                            Text(
                                text = if (onlyOwned) "✓ 已拥有" else "已拥有",
                                fontSize = 11.sp,
                                fontWeight = if (onlyOwned) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = neonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = neonGreen,
                            containerColor = Color.Transparent,
                            labelColor = textSecondary
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (onlyOwned) neonGreen else textSecondary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(30.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val intent = Intent(context, StickerExchangeActivity::class.java)
                            shopLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = neonAmber),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 16.dp).height(32.dp)
                    ) {
                        Text(
                            text = "🛒 贴纸商城",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
            )
        },

        bottomBar = {
            StickerPickerBottomTray(
                selectedStickers = selectedStickers,
                serverBaseUrl = serverBaseUrl,
                cardBg = cardBg,
                darkBg = darkBg,
                neonAmber = neonAmber,
                neonRed = neonRed,
                neonGreen = neonGreen,
                textPrimary = textPrimary,
                onDone = onDone
            )
        },
        containerColor = darkBg
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = neonBlue)
            }
        } else if (visibleSeriesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (onlyOwned) "暂无已拥有的贴纸，\n快去贴纸商城挑选喜欢的恐龙吧！ 🦖" else "暂无可用贴纸系列",
                    color = textSecondary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 左侧分类 (联动左栏)
                LazyColumn(
                    state = leftListState,
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .background(cardBg.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    itemsIndexed(visibleSeriesList) { index, series ->
                        val isSelected = firstVisibleItemIndex == index
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        rightListState.animateScrollToItem(index)
                                    }
                                }
                                .background(if (isSelected) darkBg else Color.Transparent)
                                .padding(vertical = 20.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = series.name,
                                color = if (isSelected) neonBlue else textSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // 右侧大网格 (联动右栏)
                LazyColumn(
                    state = rightListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    itemsIndexed(visibleSeriesList) { _, series ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "📁 ${series.name}",
                                color = neonAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )

                            val rawStickers = series.stickers.filter { it.is_active && !it.is_deleted }
                            val stickers = if (onlyOwned) rawStickers.filter { (remainingInventory[it.id] ?: 0) > 0 } else rawStickers
                            if (stickers.isEmpty()) {
                                Text(
                                    text = "暂无贴纸",
                                    color = textSecondary.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {

                                stickers.chunked(3).forEach { rowStickers ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowStickers.forEach { sticker ->
                                            val currentCount = remainingInventory[sticker.id] ?: 0
                                            val isAvailable = currentCount > 0

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(top = 6.dp, end = 6.dp)
                                            ) {
                                                Card(
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = CardDefaults.cardColors(containerColor = cardBg),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .border(
                                                            1.dp,
                                                            if (isAvailable) neonGreen.copy(alpha = 0.25f) else Color.Gray.copy(alpha = 0.15f),
                                                            RoundedCornerShape(12.dp)
                                                        )
                                                        .clickable {
                                                            if (isAvailable) {
                                                                if (selectedStickers.size >= 6) {
                                                                    Toast.makeText(context, "一次最多只能添加 6 张贴纸哦！", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    selectedStickers.add(sticker)
                                                                }
                                                            } else {
                                                                Toast.makeText(context, "该贴纸暂无库存，请先前往贴纸商店兑换哦 🥚", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(8.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        val localRes = com.example.dinoroar.ui.main.getStickerLocalResource(sticker.image_url)
                                                        val modelData: Any = if (localRes != null) localRes else {
                                                            if (sticker.image_url.startsWith("/static/")) serverBaseUrl + sticker.image_url else sticker.image_url
                                                        }
                                                        AsyncImage(
                                                            model = ImageRequest.Builder(LocalContext.current)
                                                                .data(modelData)
                                                                .crossfade(true)
                                                                .error(com.example.dinoroar.R.drawable.sticker_fallback_logo)
                                                                .build(),
                                                            contentDescription = sticker.name,
                                                            contentScale = ContentScale.Fit,
                                                            colorFilter = if (!isAvailable) {
                                                                androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                                                    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                                                                )
                                                            } else null,
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .graphicsLayer(alpha = if (isAvailable) 1f else 0.4f)
                                                        )

                                                        Spacer(modifier = Modifier.height(4.dp))

                                                        Text(
                                                            text = sticker.name,
                                                            color = if (isAvailable) textPrimary else textSecondary.copy(alpha = 0.6f),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }

                                                // 右上角红底白字可用库存角标 (固定三位数宽度与小巧圆角矩形)
                                                if (currentCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .offset(x = 3.dp, y = (-3).dp)
                                                            .width(24.dp)
                                                            .height(14.dp)
                                                            .clip(RoundedCornerShape(7.dp))
                                                            .background(Color(0xFFFF5252))
                                                            .border(0.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(7.dp)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = if (currentCount > 99) "99+" else currentCount.toString(),
                                                            color = Color.White,
                                                            fontSize = 8.5.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontFamily = FontFamily.Monospace,
                                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                            lineHeight = 10.sp,
                                                            style = androidx.compose.ui.text.TextStyle(
                                                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                                    includeFontPadding = false
                                                                ),
                                                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                                                                )
                                                            )
                                                        )
                                                    }

                                                }
                                            }
                                        }


                                        val remaining = 3 - rowStickers.size
                                        repeat(remaining) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
