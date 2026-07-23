package com.example.dinoroar.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
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
        get() = sharedPrefs.getString(KEY_SERVER_URL, null)
        set(value) = sharedPrefs.edit().putString(KEY_SERVER_URL, value).apply()

    var intranetUrl: String?
        get() = sharedPrefs.getString(KEY_INTRANET_URL, null)
        set(value) = sharedPrefs.edit().putString(KEY_INTRANET_URL, value).apply()

    var extranetUrl: String?
        get() = sharedPrefs.getString(KEY_EXTRANET_URL, null)
        set(value) = sharedPrefs.edit().putString(KEY_EXTRANET_URL, value).apply()

    var lockPattern: String
        get() = sharedPrefs.getString(KEY_LOCK_PATTERN, DEFAULT_PATTERN) ?: DEFAULT_PATTERN
        set(value) = sharedPrefs.edit().putString(KEY_LOCK_PATTERN, value).apply()

    var isCamouflageEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_IS_CAMOUFLAGE_ENABLED, true)
        set(value) = sharedPrefs.edit().putBoolean(KEY_IS_CAMOUFLAGE_ENABLED, value).apply()

    var isWifiOnlyEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_IS_WIFI_ONLY, true)
        set(value) = sharedPrefs.edit().putBoolean(KEY_IS_WIFI_ONLY, value).apply()

    @Volatile
    var isCameraActive: Boolean = false

    var currentThemeId: Int
        get() = sharedPrefs.getInt("current_theme_id", 8)
        set(value) = sharedPrefs.edit().putInt("current_theme_id", value).apply()

    var globalVideoQuality: String
        get() = sharedPrefs.getString("global_video_quality", "720p") ?: "720p"
        set(value) = sharedPrefs.edit().putString("global_video_quality", value).apply()

    @Volatile
    var isExternalActivityActive: Boolean = false

    var internalServerUrl: String
        get() = sharedPrefs.getString("internal_server_url", "") ?: ""
        set(value) = sharedPrefs.edit().putString("internal_server_url", value).apply()

    var externalServerUrl: String
        get() = sharedPrefs.getString("external_server_url", "") ?: ""
        set(value) = sharedPrefs.edit().putString("external_server_url", value).apply()

    var nickname: String
        get() = sharedPrefs.getString("nickname", "") ?: ""
        set(value) = sharedPrefs.edit().putString("nickname", value).apply()

    var username: String
        get() = sharedPrefs.getString("current_username", "") ?: ""
        set(value) = sharedPrefs.edit().putString("current_username", value).apply()

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

    var stickerConfigCache: String
        get() = sharedPrefs.getString("sticker_config_cache", "") ?: ""
        set(value) = sharedPrefs.edit().putString("sticker_config_cache", value).apply()

    var unlockedDinos: String
        get() = sharedPrefs.getString("unlocked_dinos", "T-Rex_proud,Triceratops,Pterodactyl_happy") ?: "T-Rex_proud,Triceratops,Pterodactyl_happy"
        set(value) = sharedPrefs.edit().putString("unlocked_dinos", value).apply()

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
    }

    fun clear() {
        sharedPrefs.edit().clear().apply()
    }
}
