package com.example.dinoroar.ui.person

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.PersonCategoryEntity
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.data.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonEditScreen(
    personUuid: String,
    isTemp: Boolean,
    defaultCategoryUuid: String?,
    repository: DataRepository,
    syncManager: SyncManager,
    onNavigateBack: () -> Unit,
    onNavigateToCategorySelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Query states
    val allCategories by repository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())
    val deletedCategories by repository.deletedCategories.collectAsStateWithLifecycle(initialValue = emptyList())
    
    // Form States with rememberSaveable to survive navigation
    var personName by rememberSaveable { mutableStateOf("") }
    var personAbbrev by rememberSaveable { mutableStateOf("") }
    var personRelation by rememberSaveable { mutableStateOf("") }
    var personCategoryUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var personColor by rememberSaveable { mutableStateOf("red") }
    var isNewMode by rememberSaveable { mutableStateOf(true) }
    var existingPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var hasLoadedInitial by rememberSaveable { mutableStateOf(false) }

    // 1. 仅在初次进入生命周期时，从数据库加载数据并初始化表单状态
    LaunchedEffect(personUuid, defaultCategoryUuid) {
        if (!hasLoadedInitial) {
            if (personUuid != "NEW") {
                val p = repository.getPersonByUuid(personUuid)
                if (p != null) {
                    personName = p.name
                    personAbbrev = p.abbreviation
                    personRelation = p.relationship
                    personCategoryUuid = p.categoryUuid
                    personColor = p.colorTag ?: "red"
                    isNewMode = false
                }
            } else {
                isNewMode = true
                personCategoryUuid = defaultCategoryUuid
            }
            hasLoadedInitial = true
        }
    }

    // 2. 持续追踪并恢复 existingPerson 实体，不受 hasLoadedInitial 限制
    // 确保在页面导航返回重建时，能安全恢复 existingPerson 状态而不破坏用户已填写的表单输入
    LaunchedEffect(personUuid) {
        if (personUuid != "NEW") {
            existingPerson = repository.getPersonByUuid(personUuid)
        }
    }

    val relationSuggestions = listOf("好朋友", "普通同学", "老师", "家长", "邻居")

    // macOS Finder tags colors
    val macOSColorMap = mapOf(
        "red" to Color(0xFFFF3B30),
        "orange" to Color(0xFFFF9500),
        "yellow" to Color(0xFFFFCC00),
        "green" to Color(0xFF34C759),
        "blue" to Color(0xFF007AFF),
        "purple" to Color(0xFFAF52DE),
        "gray" to Color(0xFF8E8E93)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewMode) "新增关系人" else if (isTemp) "🌟 临时路人转正" else "编辑人物属性", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (isTemp) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = "💡 转正提示：该人物转正后将移出“临时路人”折叠区，正式展现于您的日记点选和看板分类中！",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp),
                        lineHeight = 18.sp
                    )
                }
            }

            // Name & Abbreviation Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = personName,
                        onValueChange = {
                            personName = it
                            personAbbrev = PinyinUtils.getAbbreviation(it)
                        },
                        label = { Text("姓名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = personAbbrev,
                        onValueChange = { personAbbrev = it },
                        label = { Text("拼音首字母缩写") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Relationship & Suggestions Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = personRelation,
                        onValueChange = { personRelation = it },
                        label = { Text("关系描述") },
                        placeholder = { Text("例如: 爸爸, 英语老师, 同桌") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("常用快捷推荐:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        relationSuggestions.forEach { suggestion ->
                            val isChosen = personRelation == suggestion
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .border(1.dp, if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .clickable { personRelation = suggestion }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 11.sp,
                                    color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Category Selection (Click to jump to separate screen)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("所属分类:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    val selectedCategoryName = remember(allCategories, deletedCategories, personCategoryUuid) {
                        val activeCat = allCategories.find { it.uuid == personCategoryUuid }
                        if (activeCat != null) {
                            activeCat.name
                        } else {
                            val deletedCat = deletedCategories.find { it.uuid == personCategoryUuid }
                            if (deletedCat != null) {
                                "${deletedCat.name} (已停用)"
                            } else {
                                "无分类"
                            }
                        }
                    }


                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                com.example.dinoroar.CategorySelectorState.onCategorySelected = { uuid, _ ->
                                    personCategoryUuid = uuid
                                }
                                onNavigateToCategorySelect(personCategoryUuid)
                            }
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📂", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = selectedCategoryName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "点击修改",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "➔",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // macOS Finder Circle tag selectors
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("高亮色彩标签 (仿 macOS 标色):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        macOSColorMap.forEach { (tag, color) ->
                            val isSelected = personColor == tag
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) color else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { personColor = tag },
                                contentAlignment = Alignment.Center
                            ) {
                                // Inner solid circle
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .shadow(1.dp, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "checked",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Actions Row (All buttons in ONE single row!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Disable/Enable button (Only show if in edit mode)
                if (!isNewMode) {
                    val isCurrentlyDeleted = existingPerson?.isDeleted == true
                    Button(
                        onClick = { 
                            if (isCurrentlyDeleted) {
                                coroutineScope.launch {
                                    existingPerson?.let { p ->
                                        val activePersons = repository.getAllActivePersons()
                                        val personsInCategory = activePersons.filter { it.categoryUuid == p.categoryUuid && !it.isDeleted }
                                        val maxSort = if (personsInCategory.isEmpty()) -1 else personsInCategory.maxOf { it.sortOrder }
                                        repository.insertPerson(p.copy(isDeleted = false, sortOrder = maxSort + 1, isSynced = false))
                                        Toast.makeText(context, "人物已重新启用！", Toast.LENGTH_SHORT).show()
                                        launch(Dispatchers.IO) { syncManager.sync() }
                                        onNavigateBack()
                                    }
                                }
                            } else {
                                showDeleteConfirm = true 
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCurrentlyDeleted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            contentColor = if (isCurrentlyDeleted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isCurrentlyDeleted) "启用" else "停用", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消")
                }

                Button(
                    onClick = {
                        if (personName.isBlank()) {
                            Toast.makeText(context, "姓名不能为空！", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                            
                            // 校验如果分类为空，但分类列表有数据，默认选择第一个分类
                            var targetCategoryUuid = personCategoryUuid
                            if (targetCategoryUuid == null && allCategories.isNotEmpty()) {
                                targetCategoryUuid = allCategories.firstOrNull { !it.isDeleted }?.uuid 
                                    ?: allCategories.firstOrNull()?.uuid
                            }

                            if (targetCategoryUuid == null) {
                                Toast.makeText(context, "请先创建人物分类！所有正式关系人必须落座分类。", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            val activePersons = repository.getAllActivePersons()
                            val personsInCategory = activePersons.filter { it.categoryUuid == targetCategoryUuid && !it.isDeleted }
                            val maxSort = if (personsInCategory.isEmpty()) -1 else personsInCategory.maxOf { it.sortOrder }
                            
                            val p = existingPerson?.copy(
                                name = personName.trim(),
                                abbreviation = personAbbrev.uppercase(Locale.US),
                                relationship = personRelation.trim(),
                                categoryUuid = targetCategoryUuid,
                                sortOrder = if (existingPerson?.isTemporary == true) (maxSort + 1) else (existingPerson?.sortOrder ?: (maxSort + 1)),
                                colorTag = personColor,
                                isTemporary = false, // 转正/新建正式
                                isSynced = false
                            ) ?: PersonEntity(
                                uuid = UUID.randomUUID().toString(),
                                name = personName.trim(),
                                abbreviation = personAbbrev.uppercase(Locale.US),
                                relationship = personRelation.trim(),
                                categoryUuid = targetCategoryUuid,
                                sortOrder = maxSort + 1,
                                colorTag = personColor,
                                isTemporary = false,
                                createdAt = timeStamp,
                                isDeleted = false,
                                isSynced = false
                            )
                            repository.insertPerson(p)
                            PersonEditResultBus.postPersonSaved(p.uuid)
                            Toast.makeText(context, if (isNewMode) "添加成功！" else "保存成功", Toast.LENGTH_SHORT).show()
                            launch(Dispatchers.IO) { syncManager.sync() }
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1.2f).height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isTemp) "转正并保存" else "保存", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Disable Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认停用该关系人？", fontWeight = FontWeight.Bold) },
            text = { Text("停用后，他们将不再出现在选择名单中。您以前的日记记录依然会挂着他们的名字标签，且您可以随时在“已停用名单”中恢复他们。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        coroutineScope.launch {
                            existingPerson?.let {
                                repository.softDeletePerson(it.uuid)
                                Toast.makeText(context, "人物已停用", Toast.LENGTH_SHORT).show()
                                launch(Dispatchers.IO) { syncManager.sync() }
                                onNavigateBack()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认停用")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
