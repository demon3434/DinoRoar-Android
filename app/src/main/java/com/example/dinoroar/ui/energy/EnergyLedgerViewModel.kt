package com.example.dinoroar.ui.energy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dinoroar.network.*
import com.example.dinoroar.ui.energy.components.LedgerFilterType
import com.example.dinoroar.ui.energy.components.LedgerTimeRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * 蛋能量银行流水账本 ViewModel
 * 支持 5 维时间段过滤、收支类型过滤、后端精确分页与拟物回单
 */
class EnergyLedgerViewModel : ViewModel() {

    private val _currentFilter = MutableStateFlow(LedgerFilterType.ALL)
    val currentFilter: StateFlow<LedgerFilterType> = _currentFilter.asStateFlow()

    private val _currentTimeRange = MutableStateFlow(LedgerTimeRange.ALL)
    val currentTimeRange: StateFlow<LedgerTimeRange> = _currentTimeRange.asStateFlow()

    private val _summary = MutableStateFlow(EnergySummaryDto())
    val summary: StateFlow<EnergySummaryDto> = _summary.asStateFlow()

    private val _transactions = MutableStateFlow<List<EnergyTransactionDto>>(emptyList())
    val transactions: StateFlow<List<EnergyTransactionDto>> = _transactions.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pageSize = MutableStateFlow(20)
    val pageSize: StateFlow<Int> = _pageSize.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedReceiptTx = MutableStateFlow<EnergyTransactionDto?>(null)
    val selectedReceiptTx: StateFlow<EnergyTransactionDto?> = _selectedReceiptTx.asStateFlow()

    fun loadTransactions(apiService: DinoApiService, targetPage: Int = _currentPage.value, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _isRefreshing.value = true
            } else {
                _isLoading.value = true
            }
            _errorMessage.value = null

            try {
                val filterCode = _currentFilter.value.code
                val timeRangeCode = _currentTimeRange.value.code
                val size = _pageSize.value

                val response = apiService.getEnergyTransactions(
                    page = targetPage,
                    pageSize = size,
                    filterType = if (filterCode == "all") null else filterCode,
                    timeRange = if (timeRangeCode == "all") null else timeRangeCode
                )

                if (response.summary != null) {
                    _summary.value = response.summary
                }

                _totalCount.value = response.total
                _currentPage.value = targetPage
                _totalPages.value = if (response.total <= 0) 1 else ceil(response.total.toDouble() / size).toInt()
                _transactions.value = response.items
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "加载流水账本失败，请检查网络"
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun goToPage(page: Int, apiService: DinoApiService) {
        if (_isLoading.value) return
        val validPage = page.coerceIn(1, maxOf(1, _totalPages.value))
        loadTransactions(apiService, targetPage = validPage, isRefresh = false)
    }

    fun setPageSize(size: Int, apiService: DinoApiService) {
        if (_pageSize.value == size || _isLoading.value) return
        _pageSize.value = size
        loadTransactions(apiService, targetPage = 1, isRefresh = false)
    }

    fun setFilter(filter: LedgerFilterType, apiService: DinoApiService) {
        if (_currentFilter.value == filter) return
        _currentFilter.value = filter
        loadTransactions(apiService, targetPage = 1, isRefresh = false)
    }

    fun setTimeRange(timeRange: LedgerTimeRange, apiService: DinoApiService) {
        _currentTimeRange.value = if (_currentTimeRange.value == timeRange) {
            LedgerTimeRange.ALL // 再次点击取消筛选，恢复全部时间
        } else {
            timeRange
        }
        loadTransactions(apiService, targetPage = 1, isRefresh = false)
    }

    fun openReceipt(tx: EnergyTransactionDto) {
        _selectedReceiptTx.value = tx
    }

    fun closeReceipt() {
        _selectedReceiptTx.value = null
    }
}
