package com.example.dinoroar.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.LogEntity
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.data.local.LogPersonCrossRef
import com.example.dinoroar.data.local.PersonCategoryEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters


class MainScreenViewModel(
    private val repository: DataRepository,
    private val securePrefs: com.example.dinoroar.data.local.SecurePrefs? = null
) : ViewModel() {

    // 1. 过滤和检索状态的 Flow
    val selectedFilterPersonUuids = MutableStateFlow<Set<String>>(emptySet())
    val selectedFilterMonths = MutableStateFlow<Set<String>>(emptySet())
    val selectedFilterMoods = MutableStateFlow<Set<Int>>(emptySet())
    val searchQuery = MutableStateFlow("")

    // 2. 基础数据流
    val logs = repository.allLogsWithConfig.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allAttachments = repository.allAttachmentsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allCrossRefs = repository.allCrossRefsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allPersons = repository.allPersons.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allCategories = repository.allCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. 伴随状态：logPersonMap
    val logPersonMap: StateFlow<Map<String, List<PersonEntity>>> = combine(logs, allCrossRefs, allPersons) { logsList, crossRefsList, personsList ->
        val personMap = personsList.associateBy { it.uuid }
        val refsByLog = crossRefsList.groupBy { it.logUuid }
        logsList.associate { logWithConfig ->
            val log = logWithConfig.log
            val persons = refsByLog[log.uuid]?.mapNotNull { personMap[it.personUuid] } ?: emptyList()
            log.uuid to persons
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 3.1 伴随状态：logAttachmentsMap (按 logUuid 预聚合未删除的附件映射，供列表 O(1) 取用)
    val logAttachmentsMap: StateFlow<Map<String, List<AttachmentEntity>>> = allAttachments.map { list ->
        list.filter { !it.isDeleted && !it.logUuid.isNullOrBlank() }.groupBy { it.logUuid!! }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 3.2 贴纸配置映射与服务器基准 URL (避免卡片内重复反序列化)
    private val _stickerConfigMap = MutableStateFlow(parseStickerConfig(securePrefs?.stickerConfigCache.orEmpty()))
    val stickerConfigMap: StateFlow<Map<String, String>> = _stickerConfigMap.asStateFlow()
    val serverBaseUrl: String = securePrefs?.serverUrl?.removeSuffix("/").orEmpty()

    fun refreshStickerConfig() {
        _stickerConfigMap.value = parseStickerConfig(securePrefs?.stickerConfigCache.orEmpty())
    }

    private companion object {
        fun parseStickerConfig(cacheStr: String): Map<String, String> {
            if (cacheStr.isBlank()) return emptyMap()
            val map = mutableMapOf<String, String>()
            cacheStr.split(",").filter { it.isNotBlank() }.forEach { item ->
                val parts = item.split(":")
                if (parts.size >= 2) {
                    val id = parts[0].trim()
                    val url = parts.subList(1, parts.size).joinToString(":")
                    map[id] = url
                }
            }
            return map
        }
    }

    private data class LogFilters(
        val persons: Set<String>,
        val months: Set<String>,
        val moods: Set<Int>,
        val query: String
    )

    private val filtersFlow: Flow<LogFilters> = combine(
        selectedFilterPersonUuids,
        selectedFilterMonths,
        selectedFilterMoods,
        searchQuery
    ) { persons, months, moods, query ->
        LogFilters(persons, months, moods, query)
    }

    // 4. 经过过滤后的 filteredLogs
    val filteredLogs: StateFlow<List<com.example.dinoroar.data.local.LogWithConfig>> = combine(
        logs,
        filtersFlow,
        logPersonMap
    ) { logsList, filters, personMap ->
        logsList.filter { logWithConfig ->
            val log = logWithConfig.log
            val personMatch = if (filters.persons.isEmpty()) {
                true
            } else {
                val persons = personMap[log.uuid] ?: emptyList()
                persons.any { it.uuid in filters.persons }
            }

            val monthMatch = if (filters.months.isEmpty()) {
                true
            } else {
                filters.months.any { log.incidentDate.startsWith(it) }
            }

            val moodMatch = if (filters.moods.isEmpty()) {
                true
            } else {
                filters.moods.contains(log.moodDinoId)
            }

            val queryMatch = if (filters.query.isBlank()) {
                true
            } else {
                val titleMatch = log.title?.contains(filters.query, ignoreCase = true) == true
                val contentMatch = log.content.contains(filters.query, ignoreCase = true)
                titleMatch || contentMatch
            }
            personMatch && monthMatch && moodMatch && queryMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 5. 提取可用月份
    val availableMonths: StateFlow<List<String>> = logs.map { logsList ->
        logsList.mapNotNull { logWithConfig ->
            val log = logWithConfig.log
            try {
                if (log.incidentDate.length >= 7) log.incidentDate.substring(0, 7) else null
            } catch (e: Exception) {
                null
            }
        }.distinct().sortedDescending()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 6. 根据分类和排序规则排好序的关系人列表（仅包含活跃分类下的人物）
    val sortedPersonsForFilter: StateFlow<List<PersonEntity>> = combine(allPersons, allCategories) { personsList, categoriesList ->
        val activeCategories = categoriesList.filter { !it.isDeleted }
        val activeCategoryUuids = activeCategories.map { it.uuid }.toSet()
        val categoryOrderMap = activeCategories.mapIndexed { index, cat -> cat.uuid to index }.toMap()

        personsList
            .filter { !it.isDeleted && !it.isTemporary && it.categoryUuid != null && activeCategoryUuids.contains(it.categoryUuid) }
            .sortedWith(compareBy<PersonEntity> { person ->
                categoryOrderMap[person.categoryUuid] ?: Int.MAX_VALUE
            }.thenBy { it.sortOrder })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Helper functions for Date check
    private fun getLocalDateFromIncidentDate(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr.take(10))
        } catch (e: Exception) {
            null
        }
    }

    private fun isDateInPeriod(date: LocalDate, start: LocalDate, end: LocalDate): Boolean {
        return !date.isBefore(start) && !date.isAfter(end)
    }

    // 7. 蛋能量历史新增增量统计 Flow
    val energyDeltaSummary: StateFlow<EnergyDeltaSummary> = combine(logs, allAttachments) { logsList, attachmentsList ->
        val today = LocalDate.now()
        val startOfThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val endOfThisWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val startOfLastWeek = startOfThisWeek.minusWeeks(1)
        val endOfLastWeek = startOfThisWeek.minusDays(1)
        val startOfThisMonth = today.with(TemporalAdjusters.firstDayOfMonth())
        val endOfThisMonth = today.with(TemporalAdjusters.lastDayOfMonth())

        val mediaLogUuids = attachmentsList.filter { !it.isDeleted }.mapNotNull { it.logUuid }.toSet()

        fun calculateEnergy(filteredLogs: List<LogEntity>): Int {
            val mediaCount = filteredLogs.count { it.uuid in mediaLogUuids }
            val textCount = filteredLogs.size - mediaCount
            return mediaCount * 30 + textCount * 10
        }

        val activeLogs = logsList.map { it.log }.filter { !it.isDeleted }

        val todayLogs = activeLogs.filter { log ->
            getLocalDateFromIncidentDate(log.incidentDate)?.isEqual(today) == true
        }
        val thisWeekLogs = activeLogs.filter { log ->
            getLocalDateFromIncidentDate(log.incidentDate)?.let { isDateInPeriod(it, startOfThisWeek, endOfThisWeek) } == true
        }
        val lastWeekLogs = activeLogs.filter { log ->
            getLocalDateFromIncidentDate(log.incidentDate)?.let { isDateInPeriod(it, startOfLastWeek, endOfLastWeek) } == true
        }
        val thisMonthLogs = activeLogs.filter { log ->
            getLocalDateFromIncidentDate(log.incidentDate)?.let { isDateInPeriod(it, startOfThisMonth, endOfThisMonth) } == true
        }

        EnergyDeltaSummary(
            today = calculateEnergy(todayLogs),
            thisWeek = calculateEnergy(thisWeekLogs),
            lastWeek = calculateEnergy(lastWeekLogs),
            thisMonth = calculateEnergy(thisMonthLogs)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EnergyDeltaSummary(0, 0, 0, 0))

    // 8. 自然周期回顾数据流 (快乐时光机)
    val dashboardReviewSummary: StateFlow<DashboardReviewSummary> = combine(
        logs,
        allCrossRefs,
        allPersons
    ) { logsList, crossRefsList, personsList ->
        val today = LocalDate.now()
        val startOfThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val endOfThisWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val startOfLastWeek = startOfThisWeek.minusWeeks(1)
        val endOfLastWeek = startOfThisWeek.minusDays(1)
        
        val startOfThisMonth = today.with(TemporalAdjusters.firstDayOfMonth())
        val endOfThisMonth = today.with(TemporalAdjusters.lastDayOfMonth())
        val startOfLastMonth = startOfThisMonth.minusMonths(1)
        val endOfLastMonth = startOfThisMonth.minusDays(1)
        val startOfTwoMonthsAgo = startOfLastMonth.minusMonths(1)
        val endOfTwoMonthsAgo = startOfLastMonth.minusDays(1)

        val startOfThisYear = today.with(TemporalAdjusters.firstDayOfYear())
        val endOfThisYear = today.with(TemporalAdjusters.lastDayOfYear())
        val startOfLastYear = startOfThisYear.minusYears(1)
        val endOfLastYear = startOfThisYear.minusDays(1)

        val activeLogs = logsList.filter { !it.log.isDeleted }
        val personMap = personsList.associateBy { it.uuid }

        fun buildReviewData(
            currentPeriodLogs: List<com.example.dinoroar.data.local.LogWithConfig>,
            lastPeriodCount: Int,
            dateRangeStr: String
        ): PeriodReviewData {
            val count = currentPeriodLogs.size
            val diff = count - lastPeriodCount

            var highCount = 0
            var midCount = 0
            var lowCount = 0
            currentPeriodLogs.forEach {
                val score = it.dinoConfig?.moodScore ?: 5
                when {
                    score >= 7 -> highCount++
                    score <= 3 -> lowCount++
                    else -> midCount++
                }
            }
            val totalMoodCount = count.toFloat()
            val moodPercentages = if (count == 0) {
                listOf(0f, 0f, 0f)
            } else {
                listOf(highCount / totalMoodCount, midCount / totalMoodCount, lowCount / totalMoodCount)
            }

            val currentUuids = currentPeriodLogs.map { it.log.uuid }.toSet()
            val currentRefs = crossRefsList.filter { it.logUuid in currentUuids }
            val counts = currentRefs.groupBy { it.personUuid }.mapValues { it.value.size }
            val topPersons = counts.entries
                .mapNotNull { entry -> personMap[entry.key]?.let { it to entry.value } }
                .sortedByDescending { it.second }
                .take(3)

            return PeriodReviewData(dateRangeStr, count, diff, moodPercentages, topPersons)
        }

        val thisWeekLogs = activeLogs.filter { log ->
            getLocalDateFromIncidentDate(log.log.incidentDate)?.let { isDateInPeriod(it, startOfThisWeek, endOfThisWeek) } == true
        }
        val lastWeekLogs = activeLogs.filter { log ->
            getLocalDateFromIncidentDate(log.log.incidentDate)?.let { isDateInPeriod(it, startOfLastWeek, endOfLastWeek) } == true
        }
        val thisMonthLogs = activeLogs.filter { log ->
            getLocalDateFromIncidentDate(log.log.incidentDate)?.let { isDateInPeriod(it, startOfThisMonth, endOfThisMonth) } == true
        }
        val lastMonthLogs = activeLogs.filter { log ->
            getLocalDateFromIncidentDate(log.log.incidentDate)?.let { isDateInPeriod(it, startOfLastMonth, endOfLastMonth) } == true
        }
        val twoMonthsAgoLogs = activeLogs.filter { log ->
            getLocalDateFromIncidentDate(log.log.incidentDate)?.let { isDateInPeriod(it, startOfTwoMonthsAgo, endOfTwoMonthsAgo) } == true
        }
        val thisYearLogs = activeLogs.filter { log ->
            getLocalDateFromIncidentDate(log.log.incidentDate)?.let { isDateInPeriod(it, startOfThisYear, endOfThisYear) } == true
        }
        val lastYearLogs = activeLogs.filter { log ->
            getLocalDateFromIncidentDate(log.log.incidentDate)?.let { isDateInPeriod(it, startOfLastYear, endOfLastYear) } == true
        }

        val weekStr = "${startOfThisWeek.monthValue}月${startOfThisWeek.dayOfMonth}日 - ${endOfThisWeek.monthValue}月${endOfThisWeek.dayOfMonth}日"
        val monthStr = "${startOfThisMonth.monthValue}月1日 - ${endOfThisMonth.monthValue}月${endOfThisMonth.dayOfMonth}日"
        val lastMonthStr = "${startOfLastMonth.monthValue}月1日 - ${endOfLastMonth.monthValue}月${endOfLastMonth.dayOfMonth}日"
        val yearStr = "${startOfThisYear.year}年1月1日 - ${endOfThisYear.year}年12月31日"

        DashboardReviewSummary(
            weekReview = buildReviewData(thisWeekLogs, lastWeekLogs.size, weekStr),
            monthReview = buildReviewData(thisMonthLogs, lastMonthLogs.size, monthStr),
            lastMonthReview = buildReviewData(lastMonthLogs, twoMonthsAgoLogs.size, lastMonthStr),
            yearReview = buildReviewData(thisYearLogs, lastYearLogs.size, yearStr)
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardReviewSummary(
            weekReview = PeriodReviewData("", 0, 0, listOf(0f, 0f, 0f), emptyList()),
            monthReview = PeriodReviewData("", 0, 0, listOf(0f, 0f, 0f), emptyList()),
            lastMonthReview = PeriodReviewData("", 0, 0, listOf(0f, 0f, 0f), emptyList()),
            yearReview = PeriodReviewData("", 0, 0, listOf(0f, 0f, 0f), emptyList())
        )
    )

    // 9. 小伙伴分类心情汇总展示 Flow (分类晴雨表)
    val categoryPersonsSummary: StateFlow<Map<String, List<PersonMoodStatus>>> = combine(
        allPersons,
        logs,
        allCrossRefs
    ) { personsList, logsList, crossRefsList ->
        val activeLogs = logsList.filter { !it.log.isDeleted }
        val logMoodMap = activeLogs.associate { it.log.uuid to (it.dinoConfig?.moodScore ?: 5) }
        val logDateMap = activeLogs.associate { it.log.uuid to it.log.incidentDate }
        
        val refsByPerson = crossRefsList.groupBy { it.personUuid }

        val personSummaries = personsList.filter { !it.isDeleted }.map { person ->
            val personRefs = refsByPerson[person.uuid] ?: emptyList()
            val validRefs = personRefs.filter { it.logUuid in logMoodMap }
            val count = validRefs.size

            var happy = 0
            var calm = 0
            var sad = 0
            validRefs.forEach { ref ->
                val score = logMoodMap[ref.logUuid] ?: 5
                when {
                    score >= 7 -> happy++
                    score <= 3 -> sad++
                    else -> calm++
                }
            }

            PersonMoodStatus(
                person = person,
                diaryCount = count,
                happyCount = happy,
                calmCount = calm,
                sadCount = sad
            )
        }

        personSummaries.groupBy { it.person.categoryUuid ?: "" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun applyFilter(personUuids: Set<String>, months: Set<String>, moods: Set<Int>) {
        selectedFilterPersonUuids.value = personUuids
        selectedFilterMonths.value = months
        selectedFilterMoods.value = moods
    }

    fun clearFilter() {
        selectedFilterPersonUuids.value = emptySet()
        selectedFilterMonths.value = emptySet()
        selectedFilterMoods.value = emptySet()
    }

    fun resetState() {
        clearFilter()
        searchQuery.value = ""
    }

    // 9. 每日签到与蛋能量明细状态流
    val checkInStatus = MutableStateFlow<com.example.dinoroar.network.CheckInStatusResponse?>(null)
    val isCheckingIn = MutableStateFlow(false)
    val energyTransactions = MutableStateFlow<List<com.example.dinoroar.network.EnergyTransactionDto>>(emptyList())
    val isLoadingTransactions = MutableStateFlow(false)

    fun fetchCheckInStatus(apiService: com.example.dinoroar.network.DinoApiService) {
        viewModelScope.launch {
            try {
                val res = apiService.getCheckInStatus()
                checkInStatus.value = res
            } catch (e: Exception) {
                // 静默失败或离线降级
            }
        }
    }

    fun performCheckIn(
        apiService: com.example.dinoroar.network.DinoApiService,
        securePrefs: com.example.dinoroar.data.local.SecurePrefs,
        onSuccess: (com.example.dinoroar.network.CheckInResultResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isCheckingIn.value) return
        isCheckingIn.value = true
        viewModelScope.launch {
            try {
                val reqUuid = java.util.UUID.randomUUID().toString()
                val result = apiService.performCheckIn(com.example.dinoroar.network.CheckInRequest(reqUuid))
                securePrefs.eggEnergy = result.total_egg_energy
                // 重新刷新签到状态
                fetchCheckInStatus(apiService)
                onSuccess(result)
            } catch (e: Exception) {
                onError(e.message ?: "敲蛋签到失败，请检查网络连接")
            } finally {
                isCheckingIn.value = false
            }
        }
    }

    fun loadEnergyTransactions(apiService: com.example.dinoroar.network.DinoApiService) {
        if (isLoadingTransactions.value) return
        isLoadingTransactions.value = true
        viewModelScope.launch {
            try {
                val res = apiService.getEnergyTransactions(page = 1, pageSize = 30)
                energyTransactions.value = res.items
            } catch (e: Exception) {
                // 静默失败
            } finally {
                isLoadingTransactions.value = false
            }
        }
    }
}


// 首页看板大改版用到的纯展示数据结构
data class EnergyDeltaSummary(
    val today: Int,
    val thisWeek: Int,
    val lastWeek: Int,
    val thisMonth: Int
)

data class PeriodReviewData(
    val dateRangeStr: String,
    val count: Int,
    val diffFromLastPeriod: Int,
    val moodPercentages: List<Float>, // [高分比, 平和比, 低落比]
    val topPersons: List<Pair<PersonEntity, Int>>
)

data class DashboardReviewSummary(
    val weekReview: PeriodReviewData,
    val monthReview: PeriodReviewData,
    val lastMonthReview: PeriodReviewData,
    val yearReview: PeriodReviewData
)

data class PersonMoodStatus(
    val person: PersonEntity,
    val diaryCount: Int,
    val happyCount: Int,
    val calmCount: Int,
    val sadCount: Int
)

