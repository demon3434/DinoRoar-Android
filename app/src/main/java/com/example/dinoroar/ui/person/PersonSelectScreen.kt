package com.example.dinoroar.ui.person

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.PersonCategoryEntity
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.data.sync.SyncManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


object PersonEditResultBus {
    private val _editedPersonUuid = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val editedPersonUuid = _editedPersonUuid.asSharedFlow()

    fun postPersonSaved(uuid: String) {
        _editedPersonUuid.tryEmit(uuid)
    }
}

data class DinoColor(val bg: Color, val text: Color)

object DinoColorPalette {
    fun getColorByTag(tag: String?): DinoColor {
        return when (tag) {
            "red" -> DinoColor(Color(0xFFFFEBEE), Color(0xFFD32F2F))
            "orange" -> DinoColor(Color(0xFFFFF3E0), Color(0xFFE65100))
            "yellow" -> DinoColor(Color(0xFFFFFDE7), Color(0xFFFBC02D))
            "green" -> DinoColor(Color(0xFFE8F5E9), Color(0xFF388E3C))
            "blue" -> DinoColor(Color(0xFFE3F2FD), Color(0xFF1976D2))
            "purple" -> DinoColor(Color(0xFFF3E5F5), Color(0xFF7B1FA2))
            else -> DinoColor(Color(0xFFF5F5F5), Color(0xFF616161))
        }
    }
}

object PinyinUtils {
    fun getAbbreviation(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder()
        for (char in input) {
            val pinyin = getCharPinyin(char)
            if (pinyin.isNotEmpty()) {
                sb.append(pinyin[0].uppercaseChar())
            } else {
                if (char.isLetterOrDigit()) {
                    sb.append(char.uppercaseChar())
                }
            }
        }
        return sb.toString()
    }

    private fun getCharPinyin(c: Char): String {
        val code = c.code
        if (code in 0x4E00..0x9FA5) {
            try {
                val bytes = c.toString().toByteArray(charset("GBK"))
                if (bytes.size == 2) {
                    val value = (bytes[0].toInt() and 0xFF) shl 8 or (bytes[1].toInt() and 0xFF)
                    return when (value) {
                        in 45217..45252 -> "a"
                        in 45253..45760 -> "b"
                        in 45761..46317 -> "c"
                        in 46318..46825 -> "d"
                        in 46826..47009 -> "e"
                        in 47010..47296 -> "f"
                        in 47297..47613 -> "g"
                        in 47614..48118 -> "h"
                        in 48119..49061 -> "j"
                        in 49062..49323 -> "k"
                        in 49324..49895 -> "l"
                        in 49896..50370 -> "m"
                        in 50371..50613 -> "n"
                        in 50614..50621 -> "o"
                        in 50622..50905 -> "p"
                        in 50906..51386 -> "q"
                        in 51387..51445 -> "r"
                        in 51446..52217 -> "s"
                        in 52218..52697 -> "t"
                        in 52698..52979 -> "w"
                        in 52980..53640 -> "x"
                        in 53641..54480 -> "y"
                        in 54481..55289 -> "z"
                        else -> ""
                    }
                }
            } catch (e: Exception) {
                // ignored
            }
        }
        return ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSelectScreen(
    repository: DataRepository,
    selectedUuids: List<String>,
    onNavigateBack: () -> Unit,
    onNavigateToPersonEdit: (String, Boolean, String?) -> Unit,
    syncManager: SyncManager? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State of search and selection
    var searchQuery by remember { mutableStateOf("") }
    var currentSelectedUuids by remember { mutableStateOf(selectedUuids.toSet()) }
    var activeCategoryUuid by remember { mutableStateOf("RECENT") } // Default is RECENT

    // Database state
    val allPersons by repository.allPersons.collectAsStateWithLifecycle(initialValue = emptyList())
    val allCategories by repository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())
    var recentPersons by remember { mutableStateOf<List<PersonEntity>>(emptyList()) }

    // Dialog state for adding a formal person
    var showCreateFormalDialog by remember { mutableStateOf(false) }
    var newFormalName by remember { mutableStateOf("") }
    var newFormalAbbrev by remember { mutableStateOf("") }
    var newFormalRelation by remember { mutableStateOf("") }
    var newFormalCategoryUuid by remember { mutableStateOf<String?>(null) }
    var newFormalColor by remember { mutableStateOf("red") }

    LaunchedEffect(Unit) {
        recentPersons = repository.getRecentPersons()
        if (syncManager != null) {
            try {
                syncManager.sync()
                recentPersons = repository.getRecentPersons()
            } catch (_: Exception) {
                // 静默失败，保持无网/离线环境流畅可用
            }
        }
    }

    LaunchedEffect(Unit) {
        PersonEditResultBus.editedPersonUuid.collect { savedUuid ->
            currentSelectedUuids = currentSelectedUuids + savedUuid
        }
    }

    // Suggestions list for relationship
    val relationSuggestions = listOf("好朋友", "普通同学", "老师", "家长", "邻居")

    val activeCategoryUuids = remember(allCategories) {
        allCategories.filter { !it.isDeleted }.map { it.uuid }.toSet()
    }

    // Filtered persons based on search query or category click
    val rightPersons = remember(allPersons, recentPersons, searchQuery, activeCategoryUuid, activeCategoryUuids) {
        if (searchQuery.isNotBlank()) {
            // Global search bypasses classification selection
            allPersons.filter { person ->
                !person.isTemporary && !person.isDeleted &&
                person.categoryUuid != null && activeCategoryUuids.contains(person.categoryUuid) &&
                (person.name.contains(searchQuery, ignoreCase = true) ||
                 person.abbreviation.contains(searchQuery, ignoreCase = true))
            }
        } else {
            when (activeCategoryUuid) {
                "RECENT" -> recentPersons.filter { person ->
                    !person.isDeleted && !person.isTemporary &&
                    person.categoryUuid != null && activeCategoryUuids.contains(person.categoryUuid)
                }
                "ALL" -> allPersons.filter { person ->
                    !person.isTemporary && !person.isDeleted &&
                    person.categoryUuid != null && activeCategoryUuids.contains(person.categoryUuid)
                }
                else -> allPersons.filter { person ->
                    !person.isTemporary && !person.isDeleted &&
                    person.categoryUuid == activeCategoryUuid &&
                    activeCategoryUuids.contains(person.categoryUuid)
                }
            }
        }
    }

    // Check if the current search name exact matches any active person
    val hasExactMatch = remember(allPersons, searchQuery, activeCategoryUuids) {
        searchQuery.isBlank() || allPersons.any {
            it.name.trim() == searchQuery.trim() && !it.isDeleted &&
            it.categoryUuid != null && activeCategoryUuids.contains(it.categoryUuid)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关联关系人物", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToPersonEdit("NEW", false, null) }) {
                        Text(
                            text = "➕",
                            fontSize = 18.sp
                        )
                    }
                    TextButton(
                        onClick = {
                            com.example.dinoroar.PersonSelectorState.onPersonsSelected?.invoke(currentSelectedUuids.toList())
                            com.example.dinoroar.PersonSelectorState.onPersonsSelected = null
                            onNavigateBack()
                        }
                    ) {
                        Text("完成 (${currentSelectedUuids.size})", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            // Float selected bar
            if (currentSelectedUuids.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val selectedPersons = remember(allPersons, currentSelectedUuids) {
                            allPersons.filter { currentSelectedUuids.contains(it.uuid) }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            items(selectedPersons) { p ->
                                val colorPair = DinoColorPalette.getColorByTag(p.colorTag)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colorPair.bg)
                                        .border(1.dp, colorPair.text.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .clickable { currentSelectedUuids = currentSelectedUuids - p.uuid }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(p.name, color = colorPair.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("×", color = colorPair.text.copy(alpha = 0.6f), fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                com.example.dinoroar.PersonSelectorState.onPersonsSelected?.invoke(currentSelectedUuids.toList())
                                com.example.dinoroar.PersonSelectorState.onPersonsSelected = null
                                onNavigateBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("确定 (${currentSelectedUuids.size})")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("输入名字或首字母 (如 XM)") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "搜索") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp)
            )

            // 2. Quick Create Section
            if (searchQuery.isNotBlank() && !hasExactMatch) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "没有找到 \"${searchQuery}\"，快速将其新增为：",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    newFormalName = searchQuery.trim()
                                    newFormalAbbrev = PinyinUtils.getAbbreviation(newFormalName)
                                    newFormalRelation = ""
                                    newFormalCategoryUuid = allCategories.firstOrNull { !it.isDeleted }?.uuid
                                    newFormalColor = "red"
                                    showCreateFormalDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("正式关系人", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 3. Fused Classification left-right columns
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Left Column - Category Nav
                val leftCategories = remember(allCategories) {
                    listOf(
                        PersonCategoryEntity(uuid = "RECENT", name = "最近常用", sortOrder = -3, createdAt = ""),
                        PersonCategoryEntity(uuid = "ALL", name = "全部", sortOrder = -2, createdAt = "")
                    ) + allCategories.filter { !it.isDeleted }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(leftCategories) { cat ->
                        val isSelected = activeCategoryUuid == cat.uuid
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeCategoryUuid = cat.uuid }
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = if (cat.uuid == "RECENT") "✨ ${cat.name}" else cat.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                VerticalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )

                // Right Column - Grid List (Double column is changed to single column to avoid constraint narrow width)
                if (rightPersons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(3f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "此分类下暂无关系人哦" else "没有找到匹配的人物",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1), // Right side is narrower, single column fits best!
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(3f)
                            .fillMaxHeight()
                    ) {
                        items(rightPersons) { person ->
                            val isSelected = currentSelectedUuids.contains(person.uuid)
                            PersonSelectChip(
                                person = person,
                                isSelected = isSelected,
                                onClick = {
                                    currentSelectedUuids = if (isSelected) {
                                        currentSelectedUuids - person.uuid
                                    } else {
                                        currentSelectedUuids + person.uuid
                                    }
                                },
                                onEditClick = {
                                    onNavigateToPersonEdit(person.uuid, person.isTemporary, person.categoryUuid)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // New Formal Person Dialog
    if (showCreateFormalDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFormalDialog = false },
            title = { Text("新增正式关系人", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newFormalName,
                        onValueChange = {
                            newFormalName = it
                            newFormalAbbrev = PinyinUtils.getAbbreviation(it)
                        },
                        label = { Text("姓名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newFormalAbbrev,
                        onValueChange = { newFormalAbbrev = it },
                        label = { Text("首字母缩写") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newFormalRelation,
                        onValueChange = { newFormalRelation = it },
                        label = { Text("关系描述 (如爸爸, 同桌)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("常用推荐:", fontSize = 11.sp, color = Color.Gray)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        relationSuggestions.take(4).forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .clickable { newFormalRelation = suggestion }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(suggestion, fontSize = 11.sp)
                            }
                        }
                    }

                    val activeCategoriesList = remember(allCategories) { allCategories.filter { !it.isDeleted } }
                    if (activeCategoriesList.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        val currentCategoryName = activeCategoriesList.find { it.uuid == newFormalCategoryUuid }?.name ?: "未指定"
                        Text("所属分类:", fontSize = 11.sp, color = Color.Gray)
                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(currentCategoryName)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                activeCategoriesList.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            newFormalCategoryUuid = category.uuid
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Text("高亮色彩:", fontSize = 11.sp, color = Color.Gray)
                    val colors = listOf("red", "orange", "yellow", "green", "blue", "purple", "gray")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colors.forEach { tag ->
                            val isColorSelected = newFormalColor == tag
                            val colorPair = DinoColorPalette.getColorByTag(tag)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colorPair.bg)
                                    .border(
                                        width = if (isColorSelected) 2.dp else 1.dp,
                                        color = if (isColorSelected) colorPair.text else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { newFormalColor = tag }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isColorSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "selected",
                                        tint = colorPair.text,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFormalName.isNotBlank()) {
                            coroutineScope.launch {
                                if (newFormalCategoryUuid == null) {
                                    Toast.makeText(context, "请先在管理页面创建分类！所有正式关系人必须落座分类。", Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                                val pUuid = "p-" + UUID.randomUUID().toString()
                                val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                                val newPerson = PersonEntity(
                                    uuid = pUuid,
                                    name = newFormalName.trim(),
                                    abbreviation = newFormalAbbrev.uppercase(Locale.US),
                                    relationship = newFormalRelation.trim(),
                                    categoryUuid = newFormalCategoryUuid,
                                    sortOrder = 0,
                                    colorTag = newFormalColor,
                                    isTemporary = false,
                                    createdAt = timeStamp
                                )
                                repository.insertPerson(newPerson)
                                currentSelectedUuids = currentSelectedUuids + pUuid
                                showCreateFormalDialog = false
                                searchQuery = ""
                                Toast.makeText(context, "已新增关系人: ${newPerson.name}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "姓名不能为空！", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateFormalDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun PersonSelectChip(
    person: PersonEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val colorPair = remember(person.colorTag, person.isTemporary) {
        if (person.isTemporary) {
            DinoColorPalette.getColorByTag("gray")
        } else {
            DinoColorPalette.getColorByTag(person.colorTag)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorPair.bg)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) colorPair.text else colorPair.text.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                val displayText = if (!person.relationship.isNullOrEmpty()) {
                    "${person.name} (${person.relationship})"
                } else {
                    person.name
                }
                Text(
                    text = displayText,
                    color = colorPair.text,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "selected",
                        tint = colorPair.text,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onEditClick() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "✏️",
                        fontSize = 13.sp,
                        modifier = Modifier.graphicsLayer(scaleX = -1f)
                    )
                }
            }
        }
    }
}
