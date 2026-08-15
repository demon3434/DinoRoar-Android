package com.example.dinoroar.ui.diary

import com.example.dinoroar.ui.diary.components.CanvasPreviewDialog

import android.content.Context
import android.util.Log
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.data.local.*
import com.example.dinoroar.network.CanvasExchangeRequest
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.theme.LocalAppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasExchangeScreen(
    apiService: DinoApiService,
    securePrefs: SecurePrefs,
    canvasSeriesDao: CanvasSeriesDao,
    canvasSetDao: CanvasSetDao,
    canvasInstanceDao: CanvasInstanceDao,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val serverBaseUrl = remember { securePrefs.serverUrl?.removeSuffix("/") ?: "" }

    var isSyncing by remember { mutableStateOf(false) }
    var userInventory by remember { mutableStateOf(securePrefs.canvasInventory) }
    var eggEnergy by remember { mutableIntStateOf(securePrefs.eggEnergy) }

    // 内存中缓存每个画布套件的原价与促销标记 Map(setId -> Pair(original_price, is_on_sale))
    val priceInfoMap = remember { mutableStateMapOf<Int, Pair<Int?, Boolean>>() }

    // 观察 Room 本地数据库中的所有画布配置
    val seriesList by canvasSeriesDao.getAllActiveSeriesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val setsList by canvasSetDao.getAllActiveSetsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val instancesList by canvasInstanceDao.getAllActiveInstancesFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    // 解析出用户已解锁的商品套 ID 集合
    val unlockedSetIds = remember(userInventory) {
        userInventory.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }

    // 联动控制
    val leftListState = rememberLazyListState()
    val rightListState = rememberLazyListState()

    var showExchangeDialog by remember { mutableStateOf<CanvasSetEntity?>(null) }
    var previewInstance by remember { mutableStateOf<CanvasInstanceEntity?>(null) }

    // 初始化时从网络拉取最新的已购资产和全量配置
    LaunchedEffect(Unit) {
        isSyncing = true
        try {
            // 1. 同步全量背景配置并存入本地 Room
            val configs = apiService.getCanvasesConfig()
            val newSeries = configs.map {
                CanvasSeriesEntity(it.id, it.name, it.sort_order, it.is_active, it.is_deleted, it.created_at)
            }
            val newSets = mutableListOf<CanvasSetEntity>()
            val newInstances = mutableListOf<CanvasInstanceEntity>()
            configs.forEach { ser ->
                ser.sets.forEach { setDto ->
                    priceInfoMap[setDto.id] = Pair(setDto.original_price, setDto.is_on_sale)
                    newSets.add(CanvasSetEntity(
                        id = setDto.id,
                        seriesId = setDto.series_id,
                        name = setDto.name,
                        description = setDto.description,
                        sortOrder = setDto.sort_order,
                        exchangePrice = setDto.exchange_price,
                        isActive = setDto.is_active,
                        isDeleted = setDto.is_deleted,
                        createdAt = setDto.created_at
                    ))
                    setDto.instances.forEach { instDto ->
                        newInstances.add(CanvasInstanceEntity(
                            instDto.id,
                            instDto.canvas_set_id,
                            instDto.aspectRatio,
                            instDto.imageUrl,
                            instDto.width,
                            instDto.height,
                            instDto.isActive,
                            instDto.isDeleted,
                            instDto.createdAt
                        ))
                    }
                }
            }
            canvasSeriesDao.insertOrUpdateAll(newSeries)
            canvasSetDao.insertOrUpdateAll(newSets)
            canvasInstanceDao.insertOrUpdateAll(newInstances)

            // 2. 获取并同步用户能量及已购买背景
            val inventory = apiService.getCanvasInventory()
            securePrefs.canvasInventory = inventory.canvas_inventory
            securePrefs.eggEnergy = inventory.egg_energy
            userInventory = inventory.canvas_inventory
            eggEnergy = inventory.egg_energy
        } catch (e: Exception) {
            Log.e("CanvasExchangeScreen", "Failed to sync configs and inventory from network", e)
            coroutineScope.launch {
                Toast.makeText(context, "加载画布商城失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } finally {
            isSyncing = false
        }
    }

    var onlyUnowned by remember { mutableStateOf(false) }

    val visibleSeriesList = remember(seriesList, setsList, unlockedSetIds, onlyUnowned) {
        val sorted = seriesList.sortedBy { it.sortOrder }
        if (onlyUnowned) {
            sorted.filter { series ->
                setsList.any { it.seriesId == series.id && it.id !in unlockedSetIds }
            }
        } else {
            sorted
        }
    }

    val firstVisibleItemIndex by remember {
        derivedStateOf { rightListState.firstVisibleItemIndex }
    }

    LaunchedEffect(firstVisibleItemIndex) {
        if (visibleSeriesList.isNotEmpty() && firstVisibleItemIndex in visibleSeriesList.indices) {
            leftListState.animateScrollToItem(firstVisibleItemIndex)
        }
    }


    val appColors = LocalAppColors.current
    val darkBg = appColors.darkBg
    val cardBg = appColors.cardBg
    val neonBlue = appColors.neonBlue
    val neonAmber = appColors.neonAmber
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "画布商城 🖼️",
                        color = neonAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = neonAmber
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = onlyUnowned,
                            onClick = { onlyUnowned = !onlyUnowned },
                            label = {
                                Text(
                                    text = if (onlyUnowned) "✓ 未拥有" else "未拥有",
                                    fontSize = 11.sp,
                                    fontWeight = if (onlyUnowned) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = appColors.neonGreen.copy(alpha = 0.2f),
                                selectedLabelColor = appColors.neonGreen,
                                containerColor = Color.Transparent,
                                labelColor = textSecondary
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (onlyUnowned) appColors.neonGreen else textSecondary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(30.dp)

                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🥚", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$eggEnergy",
                                color = neonAmber,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
            )
        },
        containerColor = darkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 双栏布局实现
            if (visibleSeriesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (onlyUnowned) "全馆背景画布均已拥有，太棒啦！ 🎨" else "暂无可用背景系列",
                        color = textSecondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp)
                ) {
                    // 左栏：系列侧边定位栏 (完全对齐贴纸商城)
                    LazyColumn(
                        state = leftListState,
                        modifier = Modifier
                            .width(96.dp)
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
                                .padding(vertical = 18.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = series.name,
                                color = if (isSelected) neonBlue else textSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // 右栏：分类下画布套件大卡片列表
                LazyColumn(
                    state = rightListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 60.dp)
                ) {
                    itemsIndexed(visibleSeriesList) { _, series ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "📁 ${series.name}",
                                color = neonAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                            )

                            val rawSetsInSeries = setsList.filter { it.seriesId == series.id }
                            val setsInSeries = if (onlyUnowned) rawSetsInSeries.filter { it.id !in unlockedSetIds } else rawSetsInSeries
                            if (setsInSeries.isEmpty()) {
                                Text(
                                    text = "暂无背景画布",
                                    color = textSecondary.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            } else {

                                setsInSeries.chunked(2).forEach { rowSets ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowSets.forEach { canvasSet ->
                                            val isUnlocked = unlockedSetIds.contains(canvasSet.id)
                                            val setInstances = instancesList.filter { it.canvasSetId == canvasSet.id }

                                            // 记录当前卡片预览的 instance index，利用 canvasSet.id 隔离
                                            var currentIdx by remember(canvasSet.id) { mutableIntStateOf(0) }
                                            val currentInstance = setInstances.getOrNull(currentIdx)

                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(8.dp)
                                                ) {
                                                    // 1. 图片预览区
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(95.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color.Black.copy(alpha = 0.15f))
                                                    ) {
                                                        if (currentInstance != null) {
                                                            val fullUrl = if (currentInstance.imageUrl.startsWith("/static/")) {
                                                                serverBaseUrl + currentInstance.imageUrl
                                                            } else {
                                                                currentInstance.imageUrl
                                                            }
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(LocalContext.current)
                                                                    .data(fullUrl)
                                                                    .crossfade(true)
                                                                    .build(),
                                                                contentDescription = canvasSet.name,
                                                                contentScale = ContentScale.Fit,
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .clickable { previewInstance = currentInstance }
                                                            )

                                                            // 比例徽章
                                                            Surface(
                                                                color = Color.Black.copy(alpha = 0.6f),
                                                                shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                                                modifier = Modifier.align(Alignment.TopStart)
                                                            ) {
                                                                Text(
                                                                    text = currentInstance.aspectRatio,
                                                                    color = Color.White.copy(alpha = 0.9f),
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        } else {
                                                            Box(
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text("暂无图片", color = textSecondary, fontSize = 10.sp)
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    // 2. 左右箭头切换比例（放在图片下方，完全不重合图片，防误触，且不挤压，左右对齐上方图片框边界，宽度为32.dp且有精致描边，便于点击）
                                                    if (setInstances.size > 1) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 2.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            // 左边箭头 Box
                                                            val isLeftEnabled = currentIdx > 0
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(32.dp)
                                                                    .height(30.dp)
                                                                    .border(
                                                                        width = 1.dp,
                                                                        color = textPrimary.copy(alpha = if (isLeftEnabled) 0.25f else 0.08f),
                                                                        shape = RoundedCornerShape(6.dp)
                                                                    )
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(
                                                                        if (isLeftEnabled) cardBg.copy(alpha = 0.85f) else cardBg.copy(alpha = 0.1f)
                                                                    )
                                                                    .clickable(enabled = isLeftEnabled) { currentIdx-- },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "◀", 
                                                                    color = textPrimary.copy(alpha = if (isLeftEnabled) 0.8f else 0.15f), 
                                                                    fontSize = 11.sp
                                                                )
                                                            }

                                                            // 中间当前比例文本
                                                            Text(
                                                                text = currentInstance?.aspectRatio ?: "2:1",
                                                                color = textPrimary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.Monospace
                                                            )

                                                            // 右边箭头 Box
                                                            val isRightEnabled = currentIdx < setInstances.size - 1
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(32.dp)
                                                                    .height(30.dp)
                                                                    .border(
                                                                        width = 1.dp,
                                                                        color = textPrimary.copy(alpha = if (isRightEnabled) 0.25f else 0.08f),
                                                                        shape = RoundedCornerShape(6.dp)
                                                                    )
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(
                                                                        if (isRightEnabled) cardBg.copy(alpha = 0.85f) else cardBg.copy(alpha = 0.1f)
                                                                    )
                                                                    .clickable(enabled = isRightEnabled) { currentIdx++ },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "▶", 
                                                                    color = textPrimary.copy(alpha = if (isRightEnabled) 0.8f else 0.15f), 
                                                                    fontSize = 11.sp
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    // 2. 标题
                                                    Text(
                                                        text = canvasSet.name,
                                                        color = textPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        fontFamily = FontFamily.Monospace,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )

                                                    // 3. 说明
                                                    if (!canvasSet.description.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = canvasSet.description,
                                                            color = textSecondary,
                                                            fontSize = 9.sp,
                                                            maxLines = 1,
                                                            fontFamily = FontFamily.Monospace,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    // 4. 兑换状态和价格按钮
                                                    if (isUnlocked) {
                                                        Surface(
                                                            color = neonBlue.copy(alpha = 0.1f),
                                                            shape = RoundedCornerShape(8.dp),
                                                            border = BorderStroke(1.dp, neonBlue.copy(alpha = 0.3f)),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                text = "已拥有",
                                                                color = neonBlue,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                textAlign = TextAlign.Center,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 4.dp)
                                                            )
                                                        }
                                                    } else {
                                                        val priceInfo = priceInfoMap[canvasSet.id]
                                                        val origPrice = priceInfo?.first
                                                        val isOnSale = (priceInfo?.second == true) && (origPrice != null && origPrice > canvasSet.exchangePrice)

                                                        Button(
                                                            onClick = {
                                                                if (eggEnergy < canvasSet.exchangePrice) {
                                                                    Toast.makeText(context, "🥚 蛋能量不足，多写写日记可以积攒能量哦！", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    showExchangeDialog = canvasSet
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = if (isOnSale) Color(0xFF8B5CF6) else neonAmber
                                                            ),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(26.dp)
                                                        ) {
                                                            if (isOnSale && origPrice != null) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.Center
                                                                ) {
                                                                    Text(
                                                                        text = "🥚${canvasSet.exchangePrice}",
                                                                        color = Color.White,
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.ExtraBold,
                                                                        fontFamily = FontFamily.Monospace
                                                                    )
                                                                    Spacer(modifier = Modifier.width(3.dp))
                                                                    Text(
                                                                        text = "$origPrice",
                                                                        textDecoration = TextDecoration.LineThrough,
                                                                        color = Color.White.copy(alpha = 0.6f),
                                                                        fontSize = 8.sp,
                                                                        fontFamily = FontFamily.Monospace
                                                                    )
                                                                    Spacer(modifier = Modifier.width(3.dp))
                                                                    Text(
                                                                        text = "特惠",
                                                                        color = Color(0xFFFFD166),
                                                                        fontWeight = FontWeight.Black,
                                                                        fontSize = 7.sp
                                                                    )
                                                                }
                                                            } else {
                                                                Text(
                                                                    text = "🥚 ${canvasSet.exchangePrice} 兑换",
                                                                    color = Color.Black,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontFamily = FontFamily.Monospace
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (rowSets.size < 2) {
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

    // 1. 兑换二次确认 Dialog
    if (showExchangeDialog != null) {
        val targetSet = showExchangeDialog!!
        val priceInfo = priceInfoMap[targetSet.id]
        val origPrice = priceInfo?.first
        val isOnSale = (priceInfo?.second == true) && (origPrice != null && origPrice > targetSet.exchangePrice)

        val dialogTitle = if (isOnSale) "🎉 节日特惠兑换" else "兑换背景确认"
        val dialogMessage = if (isOnSale && origPrice != null) {
            val saved = origPrice - targetSet.exchangePrice
            "✨ 正在参与节日特惠大促！\n\n背景套件：【${targetSet.name}】\n原价：${origPrice} 蛋能量\n特惠实付：${targetSet.exchangePrice} 蛋能量\n🎉 本次兑换为您立省 ${saved} 蛋能量！\n\n兑换后该系列所有比例画布永久解锁，确认立即兑换吗？"
        } else {
            "是否消耗 🥚 ${targetSet.exchangePrice} 蛋能量兑换画布背景套件【${targetSet.name}】？\n兑换后该背景系列中所有宽高比例的画布都将一并解锁可用哦！"
        }

        AlertDialog(
            onDismissRequest = { showExchangeDialog = null },
            title = {
                Text(
                    text = dialogTitle,
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp
                )
            },
            text = {
                Text(
                    text = dialogMessage,
                    color = textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (serverBaseUrl.isBlank()) {
                            Toast.makeText(context, "⚠️ 尚未连接服务端，无法进行在线兑换", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        coroutineScope.launch {
                            isSyncing = true
                            try {
                                val syncPayload = apiService.exchangeCanvas(CanvasExchangeRequest(targetSet.id))
                                securePrefs.canvasInventory = syncPayload.canvas_inventory
                                securePrefs.eggEnergy = syncPayload.egg_energy
                                userInventory = syncPayload.canvas_inventory
                                eggEnergy = syncPayload.egg_energy
                                Toast.makeText(context, "🎉 背景套件【${targetSet.name}】兑换成功！", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("CanvasExchangeScreen", "Exchange failed", e)
                                Toast.makeText(context, "兑换失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSyncing = false
                                showExchangeDialog = null
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = if (isOnSale) Color(0xFF8B5CF6) else neonAmber)
                ) {
                    Text("✨ 立即兑换 🥚", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

            },
            dismissButton = {
                TextButton(
                    onClick = { showExchangeDialog = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = textSecondary)
                ) {
                    Text("取消", fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = cardBg,
            shape = RoundedCornerShape(16.dp),
            properties = DialogProperties(usePlatformDefaultWidth = true)
        )
    }

    // 2. 物理画布大图预览 Dialog (支持在大图模式下自由切换 4 种长宽比物理大图)
    if (previewInstance != null) {
        val currentSetInstances = instancesList.filter { it.canvasSetId == previewInstance!!.canvasSetId }
        CanvasPreviewDialog(
            instance = previewInstance!!,
            allInstancesInSet = currentSetInstances,
            serverBaseUrl = serverBaseUrl,
            onDismiss = { previewInstance = null }
        )
    }
}
