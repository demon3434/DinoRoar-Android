package com.example.dinoroar.ui.person

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dinoroar.CategorySelectorState
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.PersonCategoryEntity
import com.example.dinoroar.data.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectScreen(
    currentCategoryUuid: String?,
    repository: DataRepository,
    syncManager: SyncManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Query Categories
    val allCategories by repository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())

    // UI States
    var searchKeyword by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    // Filtered list (Always include "Unclassified" first)
    val filteredCategories = remember(allCategories, searchKeyword) {
        val list = allCategories.filter { !it.isDeleted }
        if (searchKeyword.isBlank()) {
            list
        } else {
            list.filter { it.name.contains(searchKeyword, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择所属分类", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "new_category"
                        )
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
            // Search Bar
            OutlinedTextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                placeholder = { Text("输入分类名称进行搜索...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "search") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Custom Categories
                items(filteredCategories, key = { it.uuid }) { cat ->
                    val isSelected = currentCategoryUuid == cat.uuid
                    val origIndex = allCategories.indexOfFirst { it.uuid == cat.uuid }
                    val indexPrefix = if (origIndex >= 0) "${origIndex + 1}. " else ""
                    CategorySelectItemRow(
                        name = cat.name,
                        indexPrefix = indexPrefix,
                        isSelected = isSelected,
                        onClick = {
                            CategorySelectorState.onCategorySelected?.invoke(cat.uuid, cat.name)
                            onNavigateBack()
                        }
                    )
                }

                // 2. "Unclassified" Option (Matches search empty, or search keyword containing "未")
                val matchesUnclassified = searchKeyword.isBlank() || "未分类".contains(searchKeyword, ignoreCase = true)
                if (matchesUnclassified) {
                    item(key = "UNCLASSIFIED_OPTION") {
                        val isSelected = currentCategoryUuid == null || currentCategoryUuid == "unclassified"
                        CategorySelectItemRow(
                            name = "未分类",
                            indexPrefix = "",
                            isSelected = isSelected,
                            onClick = {
                                CategorySelectorState.onCategorySelected?.invoke(null, "未分类")
                                onNavigateBack()
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Category Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新建分组/分类", fontWeight = FontWeight.Bold) },
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
                                showAddDialog = false
                                newCategoryName = ""
                                Toast.makeText(context, "分类已创建", Toast.LENGTH_SHORT).show()
                                
                                // Auto-select the newly created category and return!
                                CategorySelectorState.onCategorySelected?.invoke(cat.uuid, cat.name)
                                launch(Dispatchers.IO) { syncManager.sync() }
                                onNavigateBack()
                            }
                        }
                    }
                ) {
                    Text("创建并选择")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun CategorySelectItemRow(
    name: String,
    indexPrefix: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📂 ",
                    fontSize = 16.sp
                )
                Text(
                    text = "${indexPrefix}${name}",
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
