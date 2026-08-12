package com.example.dinoroar.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "dinoroar_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_INTRANET_URL = "intranet_url"
        private const val KEY_EXTRANET_URL = "extranet_url"
        private const val KEY_LOCK_PATTERN = "lock_pattern"
        private const val KEY_IS_CAMOUFLAGE_ENABLED = "is_camouflage_enabled"
        private const val KEY_IS_WIFI_ONLY = "is_wifi_only"
        private const val KEY_IS_CAMERA_ACTIVE = "is_camera_active"
        private const val DEFAULT_PATTERN = "1,2,3" // 霸王龙 -> 三角龙 -> 翼龙
    }

    var token: String?
        get() = sharedPrefs.getString(KEY_TOKEN, null)
        set(value) = sharedPrefs.edit().putString(KEY_TOKEN, value).apply()

    var serverUrl: String?
        get() {
            val user = username
            val key = if (user.isBlank()) KEY_SERVER_URL else "${KEY_SERVER_URL}_$user"
            return sharedPrefs.getString(key, null)
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) KEY_SERVER_URL else "${KEY_SERVER_URL}_$user"
            sharedPrefs.edit().putString(key, value).apply()
        }

    var intranetUrl: String?
        get() {
            val user = username
            val key = if (user.isBlank()) KEY_INTRANET_URL else "${KEY_INTRANET_URL}_$user"
            return sharedPrefs.getString(key, null)
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) KEY_INTRANET_URL else "${KEY_INTRANET_URL}_$user"
            sharedPrefs.edit().putString(key, value).apply()
        }

    var extranetUrl: String?
        get() {
            val user = username
            val key = if (user.isBlank()) KEY_EXTRANET_URL else "${KEY_EXTRANET_URL}_$user"
            return sharedPrefs.getString(key, null)
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) KEY_EXTRANET_URL else "${KEY_EXTRANET_URL}_$user"
            sharedPrefs.edit().putString(key, value).apply()
        }

    private val _lockPatternFlow = kotlinx.coroutines.flow.MutableStateFlow(
        run {
            val user = sharedPrefs.getString("current_username", "") ?: ""
            val key = if (user.isBlank()) KEY_LOCK_PATTERN else "${KEY_LOCK_PATTERN}_$user"
            sharedPrefs.getString(key, DEFAULT_PATTERN) ?: DEFAULT_PATTERN
        }
    )
    val lockPatternFlow: kotlinx.coroutines.flow.StateFlow<String> = _lockPatternFlow

    var lockPattern: String
        get() {
            val user = username
            val key = if (user.isBlank()) KEY_LOCK_PATTERN else "${KEY_LOCK_PATTERN}_$user"
            return sharedPrefs.getString(key, DEFAULT_PATTERN) ?: DEFAULT_PATTERN
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) KEY_LOCK_PATTERN else "${KEY_LOCK_PATTERN}_$user"
            sharedPrefs.edit().putString(key, value).apply()
            _lockPatternFlow.value = value
        }

    var isCamouflageEnabled: Boolean
        get() {
            val user = username
            val key = if (user.isBlank()) KEY_IS_CAMOUFLAGE_ENABLED else "${KEY_IS_CAMOUFLAGE_ENABLED}_$user"
            return sharedPrefs.getBoolean(key, true)
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) KEY_IS_CAMOUFLAGE_ENABLED else "${KEY_IS_CAMOUFLAGE_ENABLED}_$user"
            sharedPrefs.edit().putBoolean(key, value).apply()
        }

    var isWifiOnlyEnabled: Boolean
        get() {
            val user = username
            val key = if (user.isBlank()) KEY_IS_WIFI_ONLY else "${KEY_IS_WIFI_ONLY}_$user"
            return sharedPrefs.getBoolean(key, true)
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) KEY_IS_WIFI_ONLY else "${KEY_IS_WIFI_ONLY}_$user"
            sharedPrefs.edit().putBoolean(key, value).apply()
        }

    @Volatile
    var isCameraActive: Boolean = false

    var currentThemeId: Int
        get() {
            val user = username
            val key = if (user.isBlank()) "current_theme_id" else "current_theme_id_$user"
            return sharedPrefs.getInt(key, 8)
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) "current_theme_id" else "current_theme_id_$user"
            sharedPrefs.edit().putInt(key, value).apply()
        }

    var globalVideoQuality: String
        get() {
            val user = username
            val key = if (user.isBlank()) "global_video_quality" else "global_video_quality_$user"
            return sharedPrefs.getString(key, "720p") ?: "720p"
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) "global_video_quality" else "global_video_quality_$user"
            sharedPrefs.edit().putString(key, value).apply()
        }

    @Volatile
    var isExternalActivityActive: Boolean = false

    var internalServerUrl: String
        get() {
            val user = username
            val key = if (user.isBlank()) "internal_server_url" else "internal_server_url_$user"
            return sharedPrefs.getString(key, "") ?: ""
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) "internal_server_url" else "internal_server_url_$user"
            sharedPrefs.edit().putString(key, value).apply()
        }

    var externalServerUrl: String
        get() {
            val user = username
            val key = if (user.isBlank()) "external_server_url" else "external_server_url_$user"
            return sharedPrefs.getString(key, "") ?: ""
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) "external_server_url" else "external_server_url_$user"
            sharedPrefs.edit().putString(key, value).apply()
        }

    var nickname: String
        get() = sharedPrefs.getString("nickname", "") ?: ""
        set(value) = sharedPrefs.edit().putString("nickname", value).apply()

    private val _currentUserIdFlow = kotlinx.coroutines.flow.MutableStateFlow(username)
    val currentUserIdFlow: kotlinx.coroutines.flow.StateFlow<String> = _currentUserIdFlow.asStateFlow()

    var username: String
        get() = sharedPrefs.getString("current_username", "") ?: ""
        set(value) {
            sharedPrefs.edit().putString("current_username", value).apply()
            _currentUserIdFlow.value = value
            _lockPatternFlow.value = lockPattern
        }

    var eggEnergy: Int
        get() {
            val user = username
            val key = if (user.isBlank()) "egg_energy" else "egg_energy_$user"
            return sharedPrefs.getInt(key, 0)
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) "egg_energy" else "egg_energy_$user"
            sharedPrefs.edit().putInt(key, value).apply()
        }

    var stickerInventory: String
        get() {
            val user = username
            val key = if (user.isBlank()) "sticker_inventory" else "sticker_inventory_$user"
            return sharedPrefs.getString(key, "") ?: ""
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) "sticker_inventory" else "sticker_inventory_$user"
            sharedPrefs.edit().putString(key, value).apply()
        }

    var canvasInventory: String
        get() {
            val user = username
            val key = if (user.isBlank()) "canvas_inventory" else "canvas_inventory_$user"
            return sharedPrefs.getString(key, "") ?: ""
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) "canvas_inventory" else "canvas_inventory_$user"
            sharedPrefs.edit().putString(key, value).apply()
        }

    var stickerConfigCache: String
        get() = sharedPrefs.getString("sticker_config_cache", "") ?: ""
        set(value) = sharedPrefs.edit().putString("sticker_config_cache", value).apply()

    var unlockedDinos: String
        get() {
            val user = username
            val key = if (user.isBlank()) "unlocked_dinos" else "unlocked_dinos_$user"
            return sharedPrefs.getString(key, "T-Rex_proud,Triceratops,Pterodactyl_happy") ?: "T-Rex_proud,Triceratops,Pterodactyl_happy"
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) "unlocked_dinos" else "unlocked_dinos_$user"
            sharedPrefs.edit().putString(key, value).apply()
        }

    var hasSyncedStickers: Boolean
        get() {
            val user = username
            val key = if (user.isBlank()) "has_synced_stickers" else "has_synced_stickers_$user"
            return sharedPrefs.getBoolean(key, false)
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) "has_synced_stickers" else "has_synced_stickers_$user"
            sharedPrefs.edit().putBoolean(key, value).apply()
        }

    var lastSyncedInventory: String
        get() {
            val user = username
            val key = if (user.isBlank()) "last_synced_inventory" else "last_synced_inventory_$user"
            return sharedPrefs.getString(key, "") ?: ""
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) "last_synced_inventory" else "last_synced_inventory_$user"
            sharedPrefs.edit().putString(key, value).apply()
        }


    fun resetLockToDefault() {
        lockPattern = DEFAULT_PATTERN
        lockVersion = 1
    }

    var lockVersion: Int
        get() {
            val user = username
            val key = if (user.isBlank()) "lock_version" else "lock_version_$user"
            return sharedPrefs.getInt(key, 1)
        }
        set(value) {
            val user = username
            val key = if (user.isBlank()) "lock_version" else "lock_version_$user"
            sharedPrefs.edit().putInt(key, value).apply()
        }

    val currentUserId: String
        get() = username

    fun clear() {
        sharedPrefs.edit().clear().apply()
    }
}
