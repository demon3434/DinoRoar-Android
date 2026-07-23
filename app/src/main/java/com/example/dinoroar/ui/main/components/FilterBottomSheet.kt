package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.local.DinoConfigEntity
import com.example.dinoroar.data.local.PersonCategoryEntity
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    onDismissRequest: () -> Unit,
    availableMonths: List<String>,
    sortedPersonsForFilter: List<PersonEntity>,
    allCategories: List<PersonCategoryEntity>,
    allDinoConfigs: List<DinoConfigEntity>,
    initialSelectedPersonUuids: Set<String>,
    initialSelectedMonths: Set<String>,
    initialSelectedMoods: Set<Int>,
    onConfirmFilter: (personUuids: Set<String>, months: Set<String>, moods: Set<Int>) -> Unit
) {
    val appColors = LocalAppColors.current
    val neonBlue = appColors.neonBlue
    val neonGreen = appColors.neonGreen
    val neonAmber = appColors.neonAmber
    val neonRed = appColors.neonRed
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary
    val darkBg = appColors.darkBg
    val cardBg = appColors.cardBg

    var tempFilterPersonUuids by remember { mutableStateOf(initialSelectedPersonUuids) }
    var tempFilterMonths by remember { mutableStateOf(initialSelectedMonths) }
    var tempFilterMoods by remember { mutableStateOf(initialSelectedMoods) }
    var personSearchQuery by remember { mutableStateOf("") }

    val categoryGroupExpanded = remember { mutableStateMapOf<String?, Boolean>() }
    val yearGroupExpanded = remember { mutableStateMapOf<String, Boolean>() }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden }
        ),
        containerColor = darkBg,
        contentColor = textPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = textSecondary.copy(alpha = 0.5f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 顶部标题和关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🦖 筛选您的心情日记 🌴",
                    color = neonAmber,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = textSecondary
                    )
                }
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp)

            // ================== 1. 关系人筛选 ==================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "👥 关联人物筛选",
                    color = neonAmber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                // 检索框
                OutlinedTextField(
                    value = personSearchQuery,
                    onValueChange = { personSearchQuery = it },
                    placeholder = { Text("搜索名字/拼音首字母/关系...", color = textSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedBorderColor = neonBlue,
                        unfocusedBorderColor = textSecondary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )

                // 选项列表
                if (personSearchQuery.isNotBlank()) {
                    val filteredList = sortedPersonsForFilter.filter { person ->
                        person.name.contains(personSearchQuery, ignoreCase = true) ||
                        person.abbreviation.contains(personSearchQuery, ignoreCase = true) ||
                        person.relationship.contains(personSearchQuery, ignoreCase = true)
                    }

                    if (filteredList.isEmpty()) {
                        Text(
                            text = "未找到相关的小伙伴...",
                            color = textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            filteredList.forEach { person ->
                                val isSelected = tempFilterPersonUuids.contains(person.uuid)
                                val colorPair = remember(person.colorTag, person.isTemporary) {
                                    if (person.isTemporary) {
                                        com.example.dinoroar.ui.person.DinoColorPalette.getColorByTag("gray")
                                    } else {
                                        com.example.dinoroar.ui.person.DinoColorPalette.getColorByTag(person.colorTag)
                                    }
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        tempFilterPersonUuids = if (isSelected) {
                                            tempFilterPersonUuids - person.uuid
                                        } else {
                                            tempFilterPersonUuids + person.uuid
                                        }
                                    },
                                    label = { Text("${person.name} (${person.relationship})", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colorPair.bg,
                                        selectedLabelColor = colorPair.text,
                                        containerColor = Color.White.copy(alpha = 0.05f),
                                        labelColor = colorPair.text
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) colorPair.text else colorPair.text.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                } else {
                    val personsByCategory = sortedPersonsForFilter.groupBy { it.categoryUuid }
                    val categoryNameMap = allCategories.associate { it.uuid to it.name }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = tempFilterPersonUuids.isEmpty(),
                            onClick = { tempFilterPersonUuids = emptySet() },
                            label = { Text("全部人物", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = neonAmber,
                                selectedLabelColor = Color.Black
                            )
                        )

                        personsByCategory.forEach { (catUuid, list) ->
                            val categoryName = if (catUuid == null) "临时/未分类" else (categoryNameMap[catUuid] ?: "其他")
                            val isGroupExpanded = categoryGroupExpanded[catUuid] ?: false

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "• $categoryName",
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                val visibleList = if (isGroupExpanded) list else list.take(4)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    visibleList.forEach { person ->
                                        val isSelected = tempFilterPersonUuids.contains(person.uuid)
                                        val colorPair = remember(person.colorTag, person.isTemporary) {
                                            if (person.isTemporary) {
                                                com.example.dinoroar.ui.person.DinoColorPalette.getColorByTag("gray")
                                            } else {
                                                com.example.dinoroar.ui.person.DinoColorPalette.getColorByTag(person.colorTag)
                                            }
                                        }
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                tempFilterPersonUuids = if (isSelected) {
                                                    tempFilterPersonUuids - person.uuid
                                                } else {
                                                    tempFilterPersonUuids + person.uuid
                                                }
                                            },
                                            label = { Text("${person.name} (${person.relationship})", fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colorPair.bg,
                                                selectedLabelColor = colorPair.text,
                                                containerColor = Color.White.copy(alpha = 0.05f),
                                                labelColor = colorPair.text
                                            ),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) colorPair.text else colorPair.text.copy(alpha = 0.3f)
                                            )
                                        )
                                    }
                                    if (list.size > 4 && !isGroupExpanded) {
                                        FilterChip(
                                            selected = false,
                                            onClick = { categoryGroupExpanded[catUuid] = true },
                                            label = { Text("+ ${list.size - 4}", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = Color.White.copy(alpha = 0.1f),
                                                labelColor = neonAmber
                                            )
                                        )
                                    } else if (list.size > 4 && isGroupExpanded) {
                                        FilterChip(
                                            selected = false,
                                            onClick = { categoryGroupExpanded[catUuid] = false },
                                            label = { Text("收起", fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = Color.White.copy(alpha = 0.1f),
                                                labelColor = textSecondary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp)

            // ================== 2. 月份筛选 ==================
            if (availableMonths.isNotEmpty()) {
                val monthsByYear = availableMonths.groupBy { if (it.length >= 4) it.substring(0, 4) else "其他" }
                val sortedYears = monthsByYear.keys.sortedDescending()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "📅 选择月份",
                        color = neonAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    LaunchedEffect(sortedYears) {
                        sortedYears.forEachIndexed { index, year ->
                            if (!yearGroupExpanded.containsKey(year)) {
                                yearGroupExpanded[year] = (index == 0)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = tempFilterMonths.isEmpty(),
                            onClick = { tempFilterMonths = emptySet() },
                            label = { Text("所有月份", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = neonAmber,
                                selectedLabelColor = Color.Black
                            )
                        )

                        sortedYears.forEach { year ->
                            val isYearExpanded = yearGroupExpanded[year] ?: false
                            val months = monthsByYear[year] ?: emptyList()

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { yearGroupExpanded[year] = !isYearExpanded }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📅 $year 年",
                                        color = textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = if (isYearExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "展开折叠",
                                        tint = textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (isYearExpanded) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        months.forEach { month ->
                                            val isSelected = tempFilterMonths.contains(month)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { tempFilterMonths = if (isSelected) tempFilterMonths - month else tempFilterMonths + month },
                                                label = { Text(month, fontSize = 12.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = neonBlue,
                                                    selectedLabelColor = Color.Black
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp)

            // ================== 3. 心情筛选 ==================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🦕 恐龙心情",
                    color = neonAmber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = tempFilterMoods.isEmpty(),
                        onClick = { tempFilterMoods = emptySet() },
                        label = { Text("所有心情", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = neonAmber,
                            selectedLabelColor = Color.Black
                        )
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        allDinoConfigs.forEach { config ->
                            val moodId = config.id
                            val isSelected = tempFilterMoods.contains(moodId)
                            val dinoDisplayName = "${config.moodLabel} ${config.name}"

                            val chipColor = when (config.id) {
                                1, 2, 3, 4 -> neonGreen.copy(alpha = 0.15f)
                                5, 6 -> neonAmber.copy(alpha = 0.15f)
                                else -> neonRed.copy(alpha = 0.15f)
                            }

                            FilterChip(
                                selected = isSelected,
                                onClick = { tempFilterMoods = if (isSelected) tempFilterMoods - moodId else tempFilterMoods + moodId },
                                label = { Text(dinoDisplayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = neonBlue,
                                    selectedLabelColor = Color.Black,
                                    containerColor = chipColor,
                                    labelColor = textPrimary
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp)

            // ================== 4. 操作按钮 ==================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        tempFilterPersonUuids = emptySet()
                        tempFilterMonths = emptySet()
                        tempFilterMoods = emptySet()
                        personSearchQuery = ""
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textPrimary
                    ),
                    border = BorderStroke(1.dp, textSecondary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(text = "重置条件", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        onConfirmFilter(tempFilterPersonUuids, tempFilterMonths, tempFilterMoods)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = neonBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(text = "确认筛选", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
