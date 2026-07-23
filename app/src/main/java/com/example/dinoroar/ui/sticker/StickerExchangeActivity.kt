package com.example.dinoroar.ui.sticker

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
class StickerExchangeActivity : ComponentActivity() {

    @Inject
    lateinit var apiService: DinoApiService

    @Inject
    lateinit var securePrefs: SecurePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var themeId by remember { mutableStateOf(securePrefs.currentThemeId) }
            DinoRoarTheme(themeId = themeId) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StickerExchangeScreen(
                        apiService = apiService,
                        securePrefs = securePrefs,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerExchangeScreen(
    apiService: DinoApiService,
    securePrefs: SecurePrefs,
    onBack: () -> Unit,
    showTopAppBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var eggEnergy by remember { mutableIntStateOf(securePrefs.eggEnergy) }
    var userInventory by remember { mutableStateOf(securePrefs.stickerInventory) }
    var seriesList by remember { mutableStateOf<List<StickerSeriesDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 联动定位组件状态
    val leftListState = rememberLazyListState()
    val rightListState = rememberLazyListState()

    // 购物车与确认状态
    val cart = remember { mutableStateMapOf<Int, Int>() }
    var showCartConfirmDialog by remember { mutableStateOf(false) }
    var showCartDrawer by remember { mutableStateOf(false) }
    var isExchangingMultiple by remember { mutableStateOf(false) }

    var showExchangeDialog by remember { mutableStateOf<StickerConfigDto?>(null) }
    var isExchanging by remember { mutableStateOf(false) }

    val serverBaseUrl = remember { securePrefs.serverUrl?.removeSuffix("/") ?: "" }

    // 解析出来的已拥有贴纸库存 Map: stickerId -> count
    val inventoryMap = remember(userInventory) {
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
        map
    }

    var isOffline by remember { mutableStateOf(false) }

    // 动态拉取配置
    val loadConfig = {
        coroutineScope.launch {
            isLoading = true
            try {
                // 1. 同步拉取游戏资产状态
                val serverAsset = apiService.getStickerInventory()
                securePrefs.stickerInventory = serverAsset.sticker_inventory
                securePrefs.eggEnergy = serverAsset.egg_energy
                securePrefs.lastSyncedInventory = serverAsset.sticker_inventory
                eggEnergy = serverAsset.egg_energy
                userInventory = serverAsset.sticker_inventory

                // 2. 拉取全量嵌套系列贴纸数据
                val list = apiService.getStickersConfig()
                // 仅显示启用的系列
                seriesList = list.filter { it.is_active && !it.is_deleted }
                val cacheStr = list.flatMap { it.stickers }.joinToString(",") { "${it.id}:${it.image_url}" }
                securePrefs.stickerConfigCache = cacheStr
                isOffline = false
            } catch (e: Exception) {
                isOffline = true
                Log.w("StickerExchange", "Network load failed, falling back to local Room database: ${e.message}")
                try {
                    val db = com.example.dinoroar.data.local.DinoDatabase.MIGRATION_10_11
                    val localSeriesList = securePrefs.stickerConfigCache.split(",").filter { it.isNotBlank() }
                    Toast.makeText(context, "🌐 当前处于离线状态，已加载本地缓存贴纸", Toast.LENGTH_SHORT).show()
                } catch (dbErr: Exception) {
                    Log.e("StickerExchange", "Failed to load local DB: ${dbErr.message}")
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadConfig()
    }

    // 联动控制
    val firstVisibleItemIndex by remember {
        derivedStateOf { rightListState.firstVisibleItemIndex }
    }

    LaunchedEffect(firstVisibleItemIndex) {
        if (seriesList.isNotEmpty() && firstVisibleItemIndex in seriesList.indices) {
            leftListState.animateScrollToItem(firstVisibleItemIndex)
        }
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

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showTopAppBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = "🎨 贴纸商店",
                            color = neonAmber,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = neonAmber)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
                )
            }
        },
        bottomBar = {
            if (cart.isNotEmpty()) {
                val totalCost = cart.entries.sumOf { entry ->
                    val st = seriesList.flatMap { it.stickers }.find { it.id == entry.key }
                    (st?.exchange_price ?: 0) * entry.value
                }
                val totalQty = cart.values.sum()

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, neonBlue.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showCartDrawer = !showCartDrawer }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(36.dp)) {
                                    Text("🛒", fontSize = 24.sp)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 8.dp, y = (-8).dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(neonRed)
                                            .padding(horizontal = 5.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (totalQty > 99) "99+" else totalQty.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "共 $totalQty 张贴纸",
                                    color = textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "🥚 总消耗: $totalCost 能量 (当前: $eggEnergy)",
                                color = neonGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = { showCartConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = neonAmber),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(
                                text = "去结算",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
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
        } else if (seriesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无可用系列分类", color = textSecondary, fontSize = 14.sp)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 左侧分类 RecyclerView (美团联动左栏)
                LazyColumn(
                    state = leftListState,
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .background(cardBg.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    itemsIndexed(seriesList) { index, series ->
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

                // 右侧大网格 (美团联动右栏)
                LazyColumn(
                    state = rightListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    itemsIndexed(seriesList) { _, series ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 系列分隔标题
                            Text(
                                text = "📁 ${series.name}",
                                color = neonAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )

                            val stickers = series.stickers.filter { it.is_active && !it.is_deleted }
                            if (stickers.isEmpty()) {
                                Text(
                                    text = "暂无贴纸",
                                    color = textSecondary.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                // 模拟 Grid 实现一行 2 个
                                stickers.chunked(2).forEach { rowStickers ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowStickers.forEach { sticker ->
                                            val ownedCount = inventoryMap[sticker.id] ?: 0
                                            val isOwned = ownedCount > 0
                                            val cartQty = cart[sticker.id] ?: 0

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
                                                            if (isOwned) neonGreen.copy(alpha = 0.25f) else Color.Gray.copy(alpha = 0.15f),
                                                            RoundedCornerShape(12.dp)
                                                        )
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 4.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(start = 0.dp, end = 0.dp, top = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "🥚${sticker.exchange_price}",
                                                                color = neonAmber,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.ExtraBold
                                                            )
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(
                                                                text = sticker.name,
                                                                color = textPrimary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }

                                                        Spacer(modifier = Modifier.height(2.dp))

                                                        // 贴纸图片加载
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
                                                            colorFilter = if (isOffline) {
                                                                androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                                                    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                                                                )
                                                            } else null,
                                                            modifier = Modifier
                                                                .size(68.dp)
                                                                .graphicsLayer(alpha = if (isOffline) 0.6f else 1.0f)
                                                        )

                                                        Spacer(modifier = Modifier.height(2.dp))

                                                        val currentCartTotalCost = cart.entries.sumOf { entry ->
                                                            val st = seriesList.flatMap { it.stickers }.find { it.id == entry.key }
                                                            (st?.exchange_price ?: 0) * entry.value
                                                        }
                                                        if (cartQty > 0) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(28.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .fillMaxHeight()
                                                                        .clickable {
                                                                            if (cartQty > 1) cart[sticker.id] = cartQty - 1 else cart.remove(sticker.id)
                                                                        },
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = "－",
                                                                        color = neonBlue,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 16.sp
                                                                    )
                                                                }
                                                                Text(
                                                                    text = cartQty.toString(),
                                                                    color = textPrimary,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.width(32.dp),
                                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                                )

                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .fillMaxHeight()
                                                                        .clickable {
                                                                            if (isOffline) {
                                                                                Toast.makeText(context, "🌐 离线状态下无法完成贴纸兑换，请联网后再试", Toast.LENGTH_SHORT).show()
                                                                                return@clickable
                                                                            }
                                                                            if (cartQty < 99) {
                                                                                if (currentCartTotalCost + sticker.exchange_price > eggEnergy) {
                                                                                    Toast.makeText(context, "蛋能量不足以添加更多哦 🥚", Toast.LENGTH_SHORT).show()
                                                                                } else {
                                                                                    cart[sticker.id] = cartQty + 1
                                                                                }
                                                                            }
                                                                        },
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = "＋",
                                                                        color = neonBlue,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 16.sp
                                                                    )
                                                                }
                                                            }
                                                        } else {
                                                            Button(
                                                                onClick = {
                                                                    if (isOffline) {
                                                                        Toast.makeText(context, "🌐 离线状态下无法完成贴纸兑换，请联网后再试", Toast.LENGTH_SHORT).show()
                                                                        return@Button
                                                                    }
                                                                    if (currentCartTotalCost + sticker.exchange_price > eggEnergy) {
                                                                        Toast.makeText(context, "蛋能量不足，钱不够啦 🥚", Toast.LENGTH_SHORT).show()
                                                                    } else {
                                                                        cart[sticker.id] = 1
                                                                    }
                                                                },
                                                                shape = RoundedCornerShape(6.dp),
                                                                contentPadding = PaddingValues(0.dp),
                                                                colors = ButtonDefaults.buttonColors(containerColor = neonBlue),
                                                                modifier = Modifier
                                                                    .height(22.dp)
                                                                    .width(72.dp)
                                                            ) {
                                                                Text(
                                                                    text = "添加",
                                                                    color = Color.Black,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.ExtraBold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                // 右上角角标，外漂到外层 Box 的右上角，不遮挡恐龙图案头部
                                                if (ownedCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .offset(x = 4.dp, y = (-4).dp)
                                                            .clip(RoundedCornerShape(50))
                                                            .background(neonRed)
                                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = if (ownedCount > 99) "99+" else ownedCount.toString(),
                                                            color = Color.White,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 占位（一行 2 个时空位处理）
                                        val remaining = 2 - rowStickers.size
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

        // 购物车抽屉层（已抽离至独立组件 StickerExchangeCartDrawer）
        StickerExchangeCartDrawer(
            showCartDrawer = showCartDrawer && cart.isNotEmpty(),
            onDismiss = { showCartDrawer = false },
            cart = cart,
            seriesList = seriesList,
            serverBaseUrl = serverBaseUrl,
            eggEnergy = eggEnergy,
            cardBg = cardBg,
            neonBlue = neonBlue,
            neonRed = neonRed,
            neonAmber = neonAmber,
            textPrimary = textPrimary
        )

        // 购物车批量结算 Dialog（已抽离至独立组件 StickerExchangeConfirmDialog）
        if (showCartConfirmDialog) {
            StickerExchangeConfirmDialog(
                cart = cart,
                seriesList = seriesList,
                eggEnergy = eggEnergy,
                apiService = apiService,
                securePrefs = securePrefs,
                coroutineScope = coroutineScope,
                onExchangeFinished = { updatedEggEnergy, updatedInventory ->
                    eggEnergy = updatedEggEnergy
                    userInventory = updatedInventory
                    cart.clear()
                },
                onDismiss = { showCartConfirmDialog = false },
                cardBg = cardBg,
                neonAmber = neonAmber,
                neonRed = neonRed,
                neonBlue = neonBlue,
                neonGreen = neonGreen,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        }
    }
}
