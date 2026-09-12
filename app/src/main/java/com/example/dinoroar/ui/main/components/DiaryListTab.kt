package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.DinoConfigEntity
import com.example.dinoroar.data.local.LogEntity
import com.example.dinoroar.data.local.PersonCategoryEntity
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.data.sync.SyncManager
import com.example.dinoroar.theme.LocalAppColors
import com.example.dinoroar.ui.main.getDinoMoodLabel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListTab(
    filteredLogs: List<com.example.dinoroar.data.local.LogWithConfig>,
    logPersonMap: Map<String, List<PersonEntity>>,
    logAttachmentsMap: Map<String, List<AttachmentEntity>> = emptyMap(),
    stickerConfigMap: Map<String, String> = emptyMap(),
    serverBaseUrl: String = "",
    allPersons: List<PersonEntity>,
    allCategories: List<PersonCategoryEntity>,
    allDinoConfigs: List<DinoConfigEntity>,
    availableMonths: List<String>,
    selectedFilterPersonUuids: Set<String>,
    selectedFilterMonths: Set<String>,
    selectedFilterMoods: Set<Int>,
    sortedPersonsForFilter: List<PersonEntity>,
    onSearchQueryChange: (String) -> Unit,
    onConfirmFilter: (Set<String>, Set<String>, Set<Int>) -> Unit,
    onClearFilter: () -> Unit,
    onRemovePersonFilter: (String) -> Unit,
    onRemoveMonthFilter: (String) -> Unit,
    onRemoveMoodFilter: (Int) -> Unit,
    onDeleteLog: (String) -> Unit,
    onNavigateToCreate: (String?) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    repository: DataRepository,
    syncManager: SyncManager,
    listState: LazyListState,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val neonBlue = appColors.neonBlue
    val neonRed = appColors.neonRed
    val neonAmber = appColors.neonAmber
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary
    val cardBg = appColors.cardBg

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showFilterBottomSheet by rememberSaveable { mutableStateOf(false) }

    // 防抖搜索
    LaunchedEffect(searchQuery) {
        delay(300)
        onSearchQueryChange(searchQuery)
    }

    val isFilterActive = selectedFilterPersonUuids.isNotEmpty() || selectedFilterMonths.isNotEmpty() || selectedFilterMoods.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = textPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            interactionSource = interactionSource
                        ) { innerTextField ->
                            OutlinedTextFieldDefaults.DecorationBox(
                                value = searchQuery,
                                innerTextField = innerTextField,
                                enabled = true,
                                singleLine = true,
                                visualTransformation = VisualTransformation.None,
                                interactionSource = interactionSource,
                                placeholder = { Text("搜索记忆标题或内容...", color = textSecondary, fontSize = 13.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary,
                                    focusedBorderColor = neonBlue,
                                    unfocusedBorderColor = textSecondary.copy(alpha = 0.5f)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                container = {
                                    OutlinedTextFieldDefaults.Container(
                                        enabled = true,
                                        isError = false,
                                        interactionSource = interactionSource,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedBorderColor = neonBlue,
                                            unfocusedBorderColor = textSecondary.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        focusedBorderThickness = 1.dp,
                                        unfocusedBorderThickness = 1.dp
                                    )
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        IconButton(
                            onClick = { showFilterBottomSheet = true },
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    if (isFilterActive) neonBlue.copy(alpha = 0.15f) else cardBg,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isFilterActive) neonBlue else textSecondary.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "筛选条件",
                                tint = if (isFilterActive) neonBlue else textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        if (isFilterActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = onClearFilter,
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(neonRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .border(1.dp, neonRed, RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清除筛选",
                                    tint = neonRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    
                    if (isFilterActive) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            selectedFilterPersonUuids.forEach { uuid ->
                                val person = allPersons.find { it.uuid == uuid }
                                if (person != null) {
                                    item(key = person.uuid) {
                                        val colorPair = remember(person.colorTag, person.isTemporary) {
                                            if (person.isTemporary) {
                                                com.example.dinoroar.ui.person.DinoColorPalette.getColorByTag("gray")
                                            } else {
                                                com.example.dinoroar.ui.person.DinoColorPalette.getColorByTag(person.colorTag)
                                            }
                                        }
                                        val labelText = if (!person.relationship.isNullOrEmpty()) {
                                            "${person.name}(${person.relationship})"
                                        } else {
                                            person.name
                                        }
                                        Row(
                                            modifier = Modifier
                                                .background(colorPair.bg, RoundedCornerShape(8.dp))
                                                .border(1.dp, colorPair.text.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .clickable { onRemovePersonFilter(person.uuid) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = labelText,
                                                color = colorPair.text,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "✕",
                                                color = colorPair.text.copy(alpha = 0.8f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            selectedFilterMonths.forEach { month ->
                                item(key = "month_$month") {
                                    Row(
                                        modifier = Modifier
                                            .background(neonBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .border(1.dp, neonBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .clickable { onRemoveMonthFilter(month) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = month,
                                            color = textPrimary,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "✕",
                                            color = textPrimary.copy(alpha = 0.8f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            selectedFilterMoods.forEach { moodId ->
                                item(key = "mood_$moodId") {
                                    val dinoDisplayName = getDinoMoodLabel(moodId)
                                    Row(
                                        modifier = Modifier
                                            .background(neonAmber.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .border(1.dp, neonAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .clickable { onRemoveMoodFilter(moodId) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = dinoDisplayName,
                                            color = textPrimary,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "✕",
                                            color = textPrimary.copy(alpha = 0.8f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (filteredLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedFilterPersonUuids.isEmpty() && searchQuery.isEmpty()) "基地空空的，快去召唤你的恐龙吧！" else "没有找到符合条件的心情日记~",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                items(
                    items = filteredLogs,
                    key = { it.log.uuid }
                ) { logWithConfig ->
                    val log = logWithConfig.log
                    DiaryLogCard(
                        logWithConfig = logWithConfig,
                        attachments = logAttachmentsMap[log.uuid] ?: emptyList(),
                        associatedPersons = logPersonMap[log.uuid] ?: emptyList(),
                        onDelete = {
                            onDeleteLog(log.uuid)
                        },
                        onEdit = {
                            onNavigateToCreate(log.uuid)
                        },
                        onCardClick = {
                            onNavigateToDetail(log.uuid)
                        },
                        stickerConfigMap = stickerConfigMap,
                        serverBaseUrl = serverBaseUrl,
                        repository = repository,
                        syncManager = syncManager
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        if (showFilterBottomSheet) {
            FilterBottomSheet(
                onDismissRequest = { showFilterBottomSheet = false },
                availableMonths = availableMonths,
                sortedPersonsForFilter = sortedPersonsForFilter,
                allCategories = allCategories,
                allDinoConfigs = allDinoConfigs,
                initialSelectedPersonUuids = selectedFilterPersonUuids,
                initialSelectedMonths = selectedFilterMonths,
                initialSelectedMoods = selectedFilterMoods,
                onConfirmFilter = { personUuids, months, moods ->
                    onConfirmFilter(personUuids, months, moods)
                    showFilterBottomSheet = false
                }
            )
        }
    }
}
