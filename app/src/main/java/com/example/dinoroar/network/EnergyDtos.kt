package com.example.dinoroar.network

import kotlinx.serialization.Serializable

@Serializable
data class CheckInRequest(
    val request_uuid: String
)

@Serializable
data class CheckInRecordDto(
    val id: Int,
    val energy_reward: Int,
    val streak_bonus: Int = 0,
    val is_crit: Boolean = false,
    val streak_days: Int = 1,
    val created_at: String
)

@Serializable
data class WeeklyCheckInDayDto(
    val date: String,
    val day_of_week: Int,
    val checked_in: Boolean,
    val energy_reward: Int = 0,
    val streak_bonus: Int = 0,
    val is_crit: Boolean = false
)


@Serializable
data class CheckInStatusResponse(
    val has_checked_in_today: Boolean,
    val today_date: String,
    val streak_days: Int,
    val current_egg_energy: Int,
    val today_record: CheckInRecordDto? = null,
    val weekly_history: List<WeeklyCheckInDayDto> = emptyList()
)

@Serializable
data class CheckInResultResponse(
    val success: Boolean,
    val already_checked_in: Boolean = false,
    val checkin_id: Int,
    val total_reward: Int,
    val base_reward: Int,
    val streak_bonus: Int = 0,
    val is_crit: Boolean = false,
    val streak_days: Int,
    val total_egg_energy: Int,
    val message: String
)

@Serializable
data class EnergyAssetDisplayDto(
    val title: String,
    val subtitle: String = "",
    val badge_label: String,
    val type_icon: String = "default",
    val image_url: String? = null,
    val theme_color: String = "#10B981",
    val direction: String = "EARN",
    val detail_info: kotlinx.serialization.json.JsonObject? = null
)

@Serializable
data class EnergyTransactionDto(
    val id: Int,
    val event_type_id: Int,
    val event_name: String,
    val change_amount: Int,
    val balance_after: Int,
    val target_type_id: Int,
    val target_id: Int,
    val request_uuid: String? = null,
    val created_at: String,
    val month_group: String = "",
    val asset_display: EnergyAssetDisplayDto
)

@Serializable
data class EnergySummaryDto(
    val current_balance: Int = 0,
    val today_income: Int = 0,
    val today_expense: Int = 0,
    val week_income: Int = 0,
    val week_expense: Int = 0,
    val month_total_income: Int = 0,
    val month_total_expense: Int = 0,
    val month_net: Int = 0,
    val last_month_income: Int = 0,
    val last_month_expense: Int = 0,
    val year_income: Int = 0,
    val year_expense: Int = 0
)

@Serializable
data class EnergyTransactionPageResponse(
    val total: Int,
    val page: Int,
    val page_size: Int,
    val summary: EnergySummaryDto? = null,
    val items: List<EnergyTransactionDto> = emptyList()
)
