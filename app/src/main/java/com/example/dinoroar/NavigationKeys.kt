package com.example.dinoroar

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object CamouflageGame : NavKey
@Serializable data object NineGridLock : NavKey
@Serializable data object SetupConnection : NavKey
@Serializable data object Login : NavKey
@Serializable data object Main : NavKey
@Serializable data class LogCreate(val editingLogUuid: String? = null) : NavKey
@Serializable data object Settings : NavKey
@Serializable data object SettingsEditPattern : NavKey
@Serializable data class LogDetail(val logUuid: String) : NavKey
@Serializable data class PersonSelect(val selectedUuids: List<String> = emptyList()) : NavKey
@Serializable data object PersonCategoryManage : NavKey
@Serializable data class PersonEdit(val personUuid: String, val isTemp: Boolean, val defaultCategoryUuid: String? = null) : NavKey
@Serializable data class CategorySelect(val currentCategoryUuid: String?) : NavKey
