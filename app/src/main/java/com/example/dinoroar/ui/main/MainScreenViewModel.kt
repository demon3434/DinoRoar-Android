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
    private val repository: DataRepository
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

    // 6. 根据分类和排序规则排好序的关系人列表
    val sortedPersonsForFilter: StateFlow<List<PersonEntity>> = combine(allPersons, allCategories) { personsList, categoriesList ->
        val categoryOrderMap = categoriesList.mapIndexed { index, cat -> cat.uuid to index }.toMap()
        personsList.sortedWith(compareBy<PersonEntity> { person ->
            if (person.categoryUuid == null) {
                Int.MAX_VALUE
            } else {
                categoryOrderMap[person.categoryUuid] ?: Int.MAX_VALUE
            }
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

