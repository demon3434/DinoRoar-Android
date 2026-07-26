package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.LogPersonCrossRef
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.data.local.PersonCategoryEntity
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.ui.main.DashboardReviewSummary
import com.example.dinoroar.ui.main.EnergyDeltaSummary
import com.example.dinoroar.ui.main.PersonMoodStatus

@Composable
fun DashboardTab(
    logs: List<com.example.dinoroar.data.local.LogWithConfig>,
    allAttachments: List<AttachmentEntity>,
    allPersons: List<PersonEntity>,
    allCrossRefs: List<LogPersonCrossRef>,
    allCategories: List<PersonCategoryEntity>,
    securePrefs: SecurePrefs,
    eggEnergy: Int,
    energyDelta: EnergyDeltaSummary,
    reviewSummary: DashboardReviewSummary,
    categorySummaries: Map<String, List<PersonMoodStatus>>,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCreate: (String?) -> Unit,
    onFilterPerson: (String) -> Unit,
    onNavigateToPersonManage: () -> Unit,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 蛋能量储蓄罐 (包含余额和多维增量展示 + 兑换入口)
        item {
            StickerEnergyPouchPanel(
                eggEnergy = eggEnergy,
                energyDelta = energyDelta,
                securePrefs = securePrefs
            )
        }

        // 2. 自然周/自然月时光机回顾卡片
        item {
            NaturalPeriodReviewPanel(
                reviewSummary = reviewSummary
            )
        }

        // 3. 小伙伴分类晴雨表左右滑动卡片
        item {
            val rawLogs = remember(logs) { logs.map { it.log } }
            CategoryPersonsPagerPanel(
                categories = allCategories,
                categorySummaries = categorySummaries,
                logs = rawLogs,
                allCrossRefs = allCrossRefs,
                onFilterPerson = onFilterPerson,
                onNavigateToCreate = onNavigateToCreate
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
