package com.example.dinoroar.ui.diary

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.data.local.*
import com.example.dinoroar.network.CanvasSeriesDto
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.theme.DinoRoarTheme
import com.example.dinoroar.theme.LocalAppColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CanvasPickerActivity : ComponentActivity() {

    @Inject
    lateinit var apiService: DinoApiService

    @Inject
    lateinit var securePrefs: SecurePrefs

    @Inject
    lateinit var canvasSeriesDao: CanvasSeriesDao

    @Inject
    lateinit var canvasSetDao: CanvasSetDao

    @Inject
    lateinit var canvasInstanceDao: CanvasInstanceDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val currentInstanceId = intent.getIntExtra("current_canvas_instance_id", -1).let {
            if (it == -1) null else it
        }
        val currentRatio = intent.getStringExtra("current_canvas_aspect_ratio") ?: "2:1"

        setContent {
            val themeId by remember { mutableStateOf(securePrefs.currentThemeId) }
            DinoRoarTheme(themeId = themeId) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CanvasPickerScreen(
                        apiService = apiService,
                        securePrefs = securePrefs,
                        canvasSeriesDao = canvasSeriesDao,
                        canvasSetDao = canvasSetDao,
                        canvasInstanceDao = canvasInstanceDao,
                        currentSelectedInstanceId = currentInstanceId,
                        currentRatio = currentRatio,
                        onCancel = { finish() },
                        onSelect = { instanceId, ratio, url ->
                            val data = Intent().apply {
                                putExtra("selected_canvas_instance_id", instanceId)
                                putExtra("selected_canvas_aspect_ratio", ratio)
                                putExtra("selected_canvas_image_url", url)
                            }
                            setResult(Activity.RESULT_OK, data)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasPickerScreen(
    apiService: DinoApiService,
    securePrefs: SecurePrefs,
    canvasSeriesDao: CanvasSeriesDao,
    canvasSetDao: CanvasSetDao,
    canvasInstanceDao: CanvasInstanceDao,
    currentSelectedInstanceId: Int?,
    currentRatio: String,
    onCancel: () -> Unit,
    onSelect: (Int, String, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val serverBaseUrl = remember { securePrefs.serverUrl?.removeSuffix("/") ?: "" }

    var isSyncing by remember { mutableStateOf(false) }
    var userInventory by remember { mutableStateOf(securePrefs.canvasInventory) }
    var eggEnergy by remember { mutableIntStateOf(securePrefs.eggEnergy) }

    // 观察 Room 中的数据流
    val seriesList by canvasSeriesDao.getAllActiveSeriesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val setsList by canvasSetDao.getAllActiveSetsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val instancesList by canvasInstanceDao.getAllActiveInstancesFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    // 解析出用户已解锁的商品套 ID 集合，默认解锁首个基础免费背景套件 (ID = 3001)
    val unlockedSetIds = remember(userInventory) {
        (userInventory.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() } + 3001)
            .toSet()
    }

    // 解析初始选中的 CanvasSetId
    val initialSetId = remember(currentSelectedInstanceId, instancesList) {
        if (currentSelectedInstanceId == null || currentSelectedInstanceId == -1) {
            null
        } else {
            instancesList.find { it.id == currentSelectedInstanceId }?.canvasSetId
        }
    }
    var selectedSetId by remember { mutableStateOf<Int?>(null) }
    var selectedRatio by remember { mutableStateOf(currentRatio) }
    var hasSetInitialValue by remember { mutableStateOf(false) }

    LaunchedEffect(initialSetId) {
        if (!hasSetInitialValue && initialSetId != null) {
            selectedSetId = initialSetId
            hasSetInitialValue = true
        }
    }

    // 联动控制组件状态
    val leftListState = rememberLazyListState()
    val rightListState = rememberLazyListState()

    val activeSeries = remember(seriesList) {
        seriesList.sortedBy { it.sortOrder }
    }

    val firstVisibleItemIndex by remember {
        derivedStateOf { rightListState.firstVisibleItemIndex }
    }

    LaunchedEffect(firstVisibleItemIndex) {
        if (activeSeries.isNotEmpty() && firstVisibleItemIndex in activeSeries.indices) {
            leftListState.animateScrollToItem(firstVisibleItemIndex)
        }
    }

    // 从网络同步最新的画布资产和用户库存
    fun syncFromNetwork() {
        if (isSyncing) return
        isSyncing = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // 1. 同步服务器全量画布配置
                val configs = apiService.getCanvasesConfig()
                canvasSeriesDao.deleteAll()
                canvasSetDao.deleteAll()
                canvasInstanceDao.deleteAll()

                val newSeries = configs.map {
                    CanvasSeriesEntity(it.id, it.name, it.sort_order, it.is_active, it.is_deleted, it.created_at)
                }
                canvasSeriesDao.insertOrUpdateAll(newSeries)

                val newSets = mutableListOf<CanvasSetEntity>()
                val newInstances = mutableListOf<CanvasInstanceEntity>()
                configs.forEach { ser ->
                    ser.sets.forEach { setDto ->
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
                canvasSetDao.insertOrUpdateAll(newSets)
                canvasInstanceDao.insertOrUpdateAll(newInstances)

                // 2. 同步已购资产和能量数
                val inventory = apiService.getCanvasInventory()
                securePrefs.canvasInventory = inventory.canvas_inventory
                securePrefs.eggEnergy = inventory.egg_energy
                userInventory = inventory.canvas_inventory
                eggEnergy = inventory.egg_energy
            } catch (e: Exception) {
                Log.e("CanvasPickerActivity", "Sync from network failed", e)
            } finally {
                isSyncing = false
            }
        }
    }

    // 监听生命周期，从 Web 端商城购买后返回自动刷新
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncFromNetwork()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "🖼️ 我的画布",
                        color = neonAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = neonAmber
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val intent = Intent(context, com.example.dinoroar.ui.diary.CanvasExchangeActivity::class.java)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = neonAmber),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 16.dp).height(32.dp)
                    ) {
                        Text(
                            text = "🛒 画布商城",
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(darkBg)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
                    .padding(16.dp)
            ) {
                Column {
                    val selectedSetName = setsList.find { it.id == selectedSetId }?.name
                    val selectionText = if (selectedSetName != null) {
                        "已选: $selectedSetName ($selectedRatio)"
                    } else {
                        "未选择背景画布 (一片空白)"
                    }
                    Text(
                        text = selectionText,
                        color = neonAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                // 移除背景，点击确定
                                onSelect(-1, selectedRatio, "")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🚫 移除背景", color = textPrimary, fontSize = 13.sp)
                        }
                        Button(
                            onClick = {
                                if (selectedSetId != null) {
                                    val activeInstance = instancesList.find { it.canvasSetId == selectedSetId && it.aspectRatio == selectedRatio }
                                    val instId = activeInstance?.id ?: -1
                                    val instUrl = activeInstance?.imageUrl ?: ""
                                    onSelect(instId, selectedRatio, instUrl)
                                } else {
                                    onSelect(-1, selectedRatio, "")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = neonBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("确定 ✔", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = darkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 蛋能量展示 & 网络刷新状态
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .background(cardBg.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .border(1.dp, neonBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🥚 蛋能量: $eggEnergy",
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (isSyncing) {
                    Text(
                        text = "📡 同步中...",
                        color = neonBlue,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = "已连接秘密基地 🏠",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (activeSeries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无可用的画布分类，\n请点击右上角「画布商城」兑换解锁背景套件吧！ ✨",
                        color = textSecondary.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // 左侧分类 (联动左栏)
                    LazyColumn(
                        state = leftListState,
                        modifier = Modifier
                            .width(90.dp)
                            .fillMaxHeight()
                            .background(cardBg.copy(alpha = 0.4f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        itemsIndexed(activeSeries) { index, series ->
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
                                    .padding(vertical = 20.dp, horizontal = 8.dp),
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

                    // 右侧商品列表网格 (联动右栏)
                    LazyColumn(
                        state = rightListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        itemsIndexed(activeSeries) { _, series ->
                            val setsInSeries = setsList.filter { it.seriesId == series.id }
                                .sortedBy { it.sortOrder }

                            if (setsInSeries.isNotEmpty()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "📂 ${series.name}",
                                        color = neonAmber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                    )

                                    setsInSeries.forEach { set ->
                                        val setInstances = instancesList.filter { it.canvasSetId == set.id }
                                        CanvasSetFoodItemCard(
                                            set = set,
                                            instances = setInstances,
                                            isUnlocked = set.id in unlockedSetIds,
                                            isSelected = selectedSetId == set.id,
                                            selectedRatio = if (selectedSetId == set.id) selectedRatio else "2:1",
                                            serverBaseUrl = serverBaseUrl,
                                            textPrimary = textPrimary,
                                            textSecondary = textSecondary,
                                            cardBg = cardBg,
                                            neonBlue = neonBlue,
                                            neonAmber = neonAmber,
                                            onSelect = { ratio ->
                                                selectedSetId = set.id
                                                selectedRatio = ratio
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
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

@Composable
fun CanvasSetFoodItemCard(
    set: CanvasSetEntity,
    instances: List<CanvasInstanceEntity>,
    isUnlocked: Boolean,
    isSelected: Boolean,
    selectedRatio: String,
    serverBaseUrl: String,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    neonBlue: Color,
    neonAmber: Color,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    
    // 引入局部预览的比例状态，在外部选中状态改变或卡片切换时能自动同步
    var previewRatio by remember(set.id, selectedRatio) {
        mutableStateOf(if (isSelected) selectedRatio else instances.firstOrNull()?.aspectRatio ?: "16:9")
    }
    
    // 找到当前局部预览比例的背景图实例
    val activeInstance = instances.find { it.aspectRatio == previewRatio } ?: instances.firstOrNull()
    val displayUrl = activeInstance?.let { if (it.imageUrl.startsWith("/static/")) serverBaseUrl + it.imageUrl else it.imageUrl } ?: ""

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) cardBg.copy(alpha = 0.25f) else cardBg.copy(alpha = 0.1f)
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) neonBlue else if (isUnlocked) neonBlue.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isUnlocked) {
                    onSelect(previewRatio)
                } else {
                    Toast.makeText(context, "您尚未拥有该画布，请先前往画布商城兑换哦 🥚", Toast.LENGTH_SHORT).show()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左半部分：占满剩余宽度，垂直排列 (左上图片框，左下文字)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 左上：图片框 (纯黑背景，使用 ContentScale.Fit 显示长宽比全貌和黑边)
                Box(
                    modifier = Modifier
                        .size(width = 130.dp, height = 75.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                ) {
                    if (displayUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(displayUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = set.name,
                            contentScale = ContentScale.Fit,
                            colorFilter = if (!isUnlocked) {
                                androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                                )
                            } else null,
                            modifier = Modifier.fillMaxSize().graphicsLayer(alpha = if (!isUnlocked) 0.5f else 1f)
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("无预览图", color = textSecondary, fontSize = 10.sp)
                        }
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(neonBlue, RoundedCornerShape(bottomStart = 8.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "已选",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (!isUnlocked) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color.Gray.copy(alpha = 0.7f), RoundedCornerShape(bottomStart = 8.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🔒 未持有",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 左下：标题和备注文字
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = set.name,
                        color = if (isUnlocked) textPrimary else textPrimary.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!set.description.isNullOrBlank()) {
                        Text(
                            text = set.description,
                            color = if (isUnlocked) textSecondary else textSecondary.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 右半部分：4个长宽比按钮纵向排列，贴着卡片右边放置
            val ratios = listOf("16:9", "4:3", "1:1", "2:1")
            Column(
                modifier = Modifier.width(54.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.End
            ) {
                ratios.forEach { ratio ->
                    val isRatioActive = previewRatio == ratio
                    val hasInstance = instances.any { it.aspectRatio == ratio }

                    val chipBg = if (isRatioActive) {
                        if (isSelected) neonBlue else neonBlue.copy(alpha = 0.6f)
                    } else if (isUnlocked) {
                        cardBg.copy(alpha = 0.5f)
                    } else {
                        cardBg.copy(alpha = 0.15f)
                    }

                    val chipBorderColor = if (isRatioActive) {
                        if (isSelected) neonBlue else neonBlue.copy(alpha = 0.6f)
                    } else if (isUnlocked && hasInstance) {
                        Color.Gray.copy(alpha = 0.4f)
                    } else {
                        Color.Gray.copy(alpha = 0.15f)
                    }

                    val chipTextColor = if (isRatioActive) {
                        Color.White
                    } else if (isUnlocked) {
                        textSecondary
                    } else {
                        textSecondary.copy(alpha = 0.4f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, chipBorderColor, RoundedCornerShape(6.dp))
                            .background(chipBg, RoundedCornerShape(6.dp))
                            .clickable {
                                previewRatio = ratio
                                if (isUnlocked) {
                                    onSelect(ratio)
                                }
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ratio,
                            fontSize = 9.sp,
                            color = chipTextColor,
                            fontWeight = if (isRatioActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
