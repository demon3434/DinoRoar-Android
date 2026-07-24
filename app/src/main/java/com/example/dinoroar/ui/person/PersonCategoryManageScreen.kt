package com.example.dinoroar.ui.person

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PersonCategoryManageScreen(
    repository: DataRepository,
    syncManager: SyncManager,
    onNavigateBack: () -> Unit,
    onNavigateToPersonEdit: (String, Boolean, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Database states
    val allPersons by repository.allPersons.collectAsStateWithLifecycle(initialValue = emptyList())
    val allCategories by repository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())
    val allPersonsWithTemp by produceState<List<PersonEntity>>(initialValue = emptyList(), allPersons) {
        value = repository.getAllPersonsWithTemporary()
    }
    val deletedPersons by repository.deletedPersons.collectAsStateWithLifecycle(initialValue = emptyList())
    val deletedCategories by repository.deletedCategories.collectAsStateWithLifecycle(initialValue = emptyList())

    // UI States
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    var showEditCategoryDialog by remember { mutableStateOf<PersonCategoryEntity?>(null) }
    var editCategoryName by remember { mutableStateOf("") }

    var categoryToDelete by remember { mutableStateOf<PersonCategoryEntity?>(null) }

    var isReorderMode by remember { mutableStateOf(false) } 
    var isSyncing by remember { mutableStateOf(false) }

    // Temporary lists fold state
    var isTempListExpanded by remember { mutableStateOf(false) }
    var isDeletedListExpanded by remember { mutableStateOf(false) }

    // Group active formal persons
    val groupedPersons = remember(allPersons) {
        val formal = allPersons.filter { !it.isTemporary }
        formal.groupBy { it.categoryUuid }
    }

    // Split formal and temporary
    val temporaryPersons = remember(allPersonsWithTemp) {
        allPersonsWithTemp.filter { it.isTemporary }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理关系人与分类", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Pull / Sync from cloud button
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                isSyncing = true
                                try {
                                    syncManager.sync(isManual = true)
                                    Toast.makeText(context, "云端拉取同步成功！", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "同步失败: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isSyncing = false
                                }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "云端拉取同步", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    IconButton(onClick = { isReorderMode = !isReorderMode }) {
                        Icon(
                            imageVector = if (isReorderMode) Icons.Default.Done else Icons.Default.SwapVert,
                            contentDescription = "调整顺序",
                            tint = if (isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "添加分类")
                    }

                    IconButton(onClick = { onNavigateToPersonEdit("NEW", false, null) }) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "添加正式关系人")
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
        ) {
            if (isReorderMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 排序模式已开启：使用右侧的 ▲ / ▼ 气泡微调分类和人物的顺序吧！",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 渲染用户自定义分类
                itemsIndexed(
                    items = allCategories,
                    key = { _, cat -> cat.uuid }
                ) { index, category ->
                    val personsInCategory = groupedPersons[category.uuid] ?: emptyList()
                    CategorySection(
                        category = category,
                        persons = personsInCategory,
                        index = index,
                        totalCategories = allCategories.size,
                        isReorderMode = isReorderMode,
                        onEditCategory = {
                            editCategoryName = category.name
                            showEditCategoryDialog = category
                        },
                        onDeleteCategory = {
                            categoryToDelete = category
                        },
                        onMoveCategoryUp = {
                            coroutineScope.launch {
                                val mutable = allCategories.toMutableList()
                                val temp = mutable[index]
                                mutable[index] = mutable[index - 1]
                                mutable[index - 1] = temp
                                repository.updateCategoriesOrder(mutable)
                                launch(Dispatchers.IO) { syncManager.sync() }
                            }
                        },
                        onMoveCategoryDown = {
                            coroutineScope.launch {
                                val mutable = allCategories.toMutableList()
                                val temp = mutable[index]
                                mutable[index] = mutable[index + 1]
                                mutable[index + 1] = temp
                                repository.updateCategoriesOrder(mutable)
                                launch(Dispatchers.IO) { syncManager.sync() }
                            }
                        },
                        onAddPersonToCategory = {
                            onNavigateToPersonEdit("NEW", false, category.uuid)
                        },
                        onEditPerson = { person ->
                            onNavigateToPersonEdit(person.uuid, false, null)
                        },
                        onMovePersonUp = { personIndex ->
                            coroutineScope.launch {
                                val mutable = personsInCategory.toMutableList()
                                  val temp = mutable[personIndex]
                                  mutable[personIndex] = mutable[personIndex - 1]
                                  mutable[personIndex - 1] = temp
                                  repository.updatePersonsOrder(mutable)
                                  launch(Dispatchers.IO) { syncManager.sync() }
                            }
                        },
                        onMovePersonDown = { personIndex ->
                            coroutineScope.launch {
                                val mutable = personsInCategory.toMutableList()
                                  val temp = mutable[personIndex]
                                  mutable[personIndex] = mutable[personIndex + 1]
                                  mutable[personIndex + 1] = temp
                                  repository.updatePersonsOrder(mutable)
                                  launch(Dispatchers.IO) { syncManager.sync() }
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                // 2. 渲染“未分类”栏 (静态栏，不可删除)
                val unclassifiedPersons = groupedPersons[null] ?: emptyList()
                if (unclassifiedPersons.isNotEmpty() || allCategories.isEmpty()) {
                    item(key = "STATIC_UNCLASSIFIED") {
                        CategorySection(
                            category = PersonCategoryEntity(uuid = "OTHER", name = "未分类", sortOrder = -1, createdAt = ""),
                            persons = unclassifiedPersons,
                            index = 0,
                            totalCategories = 1,
                            isReorderMode = isReorderMode,
                            onEditCategory = {},
                            onDeleteCategory = {},
                            onMoveCategoryUp = {},
                            onMoveCategoryDown = {},
                            onAddPersonToCategory = {
                                onNavigateToPersonEdit("NEW", false, null)
                            },
                            onEditPerson = { person ->
                                onNavigateToPersonEdit(person.uuid, false, null)
                            },
                            onMovePersonUp = { personIndex ->
                                coroutineScope.launch {
                                    val mutable = unclassifiedPersons.toMutableList()
                                    val temp = mutable[personIndex]
                                    mutable[personIndex] = mutable[personIndex - 1]
                                    mutable[personIndex - 1] = temp
                                    repository.updatePersonsOrder(mutable)
                                }
                            },
                            onMovePersonDown = { personIndex ->
                                coroutineScope.launch {
                                    val mutable = unclassifiedPersons.toMutableList()
                                    val temp = mutable[personIndex]
                                    mutable[personIndex] = mutable[personIndex + 1]
                                    mutable[personIndex + 1] = temp
                                    repository.updatePersonsOrder(mutable)
                                }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                // 3. 渲染“一次性临时人物”折叠区 (核心防污染机制)
                if (temporaryPersons.isNotEmpty()) {
                    item(key = "STATIC_TEMPORARY") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .animateItem()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isTempListExpanded = !isTempListExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (isTempListExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                        contentDescription = "fold"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "📁 一次性临时路人名单 (${temporaryPersons.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }

                                AnimatedVisibility(visible = isTempListExpanded) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .padding(top = 12.dp)
                                            .fillMaxWidth()
                                     ) {
                                        Text(
                                            text = "ℹ️ 这些人物是在写日记时快捷录入的“路人”，不会显示在点选名单和正式分类中。如果需要，你可以点击他们将其“转正”。",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            lineHeight = 16.sp
                                        )
                                        temporaryPersons.forEach { person ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        // 点击临时人物，跳转到转正页面
                                                        onNavigateToPersonEdit(person.uuid, true, null)
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = person.name,
                                                    fontSize = 13.sp,
                                                    color = Color.Gray,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = "点击转正 ➔",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (deletedPersons.isNotEmpty() || deletedCategories.isNotEmpty()) {
                    item(key = "STATIC_DELETED_ARCHIVE") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .animateItem()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isDeletedListExpanded = !isDeletedListExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (isDeletedListExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                        contentDescription = "fold"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "📁 已停用的关系人与分类 (${deletedPersons.size + deletedCategories.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }

                                AnimatedVisibility(visible = isDeletedListExpanded) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .padding(top = 12.dp)
                                            .fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "ℹ️ 您可以在此处随时“重新启用”被停用的分类和人物，恢复后他们将重现于日记点选与分类看板中。以前关联的历史日记在停用期间同样安全保留。",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            lineHeight = 16.sp
                                        )

                                        // 1. 列出已停用的分类
                                        if (deletedCategories.isNotEmpty()) {
                                            Text("已停用的分类：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                            deletedCategories.forEach { cat ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("📂 ${cat.name}", fontSize = 13.sp)
                                                    TextButton(onClick = {
                                                        coroutineScope.launch {
                                                            repository.insertCategory(cat.copy(isDeleted = false))
                                                            Toast.makeText(context, "分类 '${cat.name}' 已恢复启用！", Toast.LENGTH_SHORT).show()
                                                            launch(Dispatchers.IO) { syncManager.sync() }
                                                        }
                                                    }) {
                                                        Text("恢复启用", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        // 2. 列出已停用的人物
                                        if (deletedPersons.isNotEmpty()) {
                                            Text("已停用的关系人：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                            deletedPersons.forEach { p ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            onNavigateToPersonEdit(p.uuid, false, null)
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val colorPair = DinoColorPalette.getColorByTag(p.colorTag)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                        if (!p.relationship.isNullOrEmpty()) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(colorPair.bg)
                                                                    .border(1.dp, colorPair.text.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text(p.relationship, color = colorPair.text, fontSize = 9.sp)
                                                            }
                                                        }
                                                    }
                                                    TextButton(onClick = {
                                                        coroutineScope.launch {
                                                            repository.insertPerson(p.copy(isDeleted = false, isSynced = false))
                                                            Toast.makeText(context, "关系人 '${p.name}' 已恢复启用！", Toast.LENGTH_SHORT).show()
                                                            launch(Dispatchers.IO) { syncManager.sync() }
                                                        }
                                                    }) {
                                                        Text("恢复启用", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        }
    }

    // 1. 添加分类 Dialog
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("新增人物分类", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("分类名称 (例如: 家人, 死党)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            coroutineScope.launch {
                                val cat = PersonCategoryEntity(
                                    uuid = "cat-" + UUID.randomUUID().toString(),
                                    name = newCategoryName.trim(),
                                    sortOrder = allCategories.size,
                                    createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                                )
                                repository.insertCategory(cat)
                                showAddCategoryDialog = false
                                newCategoryName = ""
                                Toast.makeText(context, "分类已创建", Toast.LENGTH_SHORT).show()
                                launch(Dispatchers.IO) { syncManager.sync() }
                            }
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddCategoryDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 2. 修改分类 Dialog
    showEditCategoryDialog?.let { category ->
        AlertDialog(
            onDismissRequest = { showEditCategoryDialog = null },
            title = { Text("修改分类名称", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editCategoryName,
                    onValueChange = { editCategoryName = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editCategoryName.isNotBlank()) {
                            coroutineScope.launch {
                                val updated = category.copy(name = editCategoryName.trim())
                                repository.insertCategory(updated)
                                showEditCategoryDialog = null
                                Toast.makeText(context, "已更新", Toast.LENGTH_SHORT).show()
                                launch(Dispatchers.IO) { syncManager.sync() }
                            }
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditCategoryDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 3. 删除分类 确认 Dialog
    categoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("确认停用分类 '${category.name}'？", fontWeight = FontWeight.Bold) },
            text = { Text("停用分类后，该分类下的人员不会被删除，而是会自动转移到“未分类”中。您以前日记中的分类历史记录同样完整安全，且您可以随时在下方“已停用名单”中恢复启用此分类。") },
            confirmButton = {
                Button(
                    onClick = {
                        categoryToDelete = null
                        coroutineScope.launch {
                            repository.deleteCategory(category.uuid)
                            Toast.makeText(context, "分类已停用，人员已转移至'未分类'", Toast.LENGTH_SHORT).show()
                            launch(Dispatchers.IO) { syncManager.sync() }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认停用")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { categoryToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun CategorySection(
    category: PersonCategoryEntity,
    persons: List<PersonEntity>,
    index: Int,
    totalCategories: Int,
    isReorderMode: Boolean,
    onEditCategory: () -> Unit,
    onDeleteCategory: () -> Unit,
    onMoveCategoryUp: () -> Unit,
    onMoveCategoryDown: () -> Unit,
    onAddPersonToCategory: () -> Unit,
    onEditPerson: (PersonEntity) -> Unit,
    onMovePersonUp: (Int) -> Unit,
    onMovePersonDown: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isStaticCategory = category.uuid == "OTHER"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Category Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📂 " + category.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${persons.size}人)",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                if (isReorderMode && !isStaticCategory) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onMoveCategoryUp,
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "up",
                                tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(
                            onClick = onMoveCategoryDown,
                            enabled = index < totalCategories - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "down",
                                tint = if (index < totalCategories - 1) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Add person to this category button (👥+)
                        IconButton(onClick = onAddPersonToCategory, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "在此分类下新增关系人",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        if (!isStaticCategory) {
                            IconButton(onClick = onEditCategory, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "edit",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(onClick = onDeleteCategory, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "delete",
                                    tint = Color.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category members list
            if (persons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无成员",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    persons.forEachIndexed { personIndex, person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onEditPerson(person) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val colorPair = remember(person.colorTag) {
                                DinoColorPalette.getColorByTag(person.colorTag)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = person.name,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!person.relationship.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colorPair.bg)
                                            .border(1.dp, colorPair.text.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = person.relationship,
                                            color = colorPair.text,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (isReorderMode) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { onMovePersonUp(personIndex) },
                                        enabled = personIndex > 0,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "up",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (personIndex > 0) MaterialTheme.colorScheme.secondary else Color.Gray.copy(alpha = 0.4f)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMovePersonDown(personIndex) },
                                        enabled = personIndex < persons.size - 1,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "down",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (personIndex < persons.size - 1) MaterialTheme.colorScheme.secondary else Color.Gray.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = "edit",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
