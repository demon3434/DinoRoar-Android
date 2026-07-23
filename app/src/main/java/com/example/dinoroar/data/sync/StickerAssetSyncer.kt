package com.example.dinoroar.data.sync

import android.util.Log
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.data.local.StickerDao
import com.example.dinoroar.data.local.StickerEntity
import com.example.dinoroar.data.local.StickerSeriesDao
import com.example.dinoroar.data.local.StickerSeriesEntity
import com.example.dinoroar.network.DinoApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerAssetSyncer @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val apiService: DinoApiService,
    private val stickerDao: StickerDao,
    private val stickerSeriesDao: StickerSeriesDao
) {
    private val TAG = "StickerAssetSyncer"

    suspend fun syncStickerConfigDown() = withContext(Dispatchers.IO) {
        try {
            val seriesListDto = apiService.getStickersConfig()
            val seriesEntities = seriesListDto.map { sDto ->
                StickerSeriesEntity(
                    id = sDto.id,
                    name = sDto.name,
                    sortOrder = sDto.sort_order,
                    isActive = sDto.is_active,
                    isDeleted = sDto.is_deleted,
                    createdAt = sDto.created_at
                )
            }
            val stickerEntities = seriesListDto.flatMap { sDto ->
                sDto.stickers.map { stDto ->
                    StickerEntity(
                        id = stDto.id,
                        seriesId = stDto.series_id ?: sDto.id,
                        name = stDto.name,
                        imageUrl = stDto.image_url,
                        description = stDto.description,
                        sortOrder = stDto.sort_order,
                        exchangePrice = stDto.exchange_price,
                        isActive = stDto.is_active,
                        isDeleted = stDto.is_deleted,
                        createdAt = stDto.created_at
                    )
                }
            }
            stickerSeriesDao.insertOrUpdateAll(seriesEntities)
            stickerDao.insertOrUpdateAll(stickerEntities)

            val cacheStr = stickerEntities.joinToString(",") { "${it.id}:${it.imageUrl}" }
            securePrefs.stickerConfigCache = cacheStr
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull sticker configs: ${e.message}", e)
        }
    }

    suspend fun syncStickerInventoryDown() = withContext(Dispatchers.IO) {
        try {
            syncStickerConfigDown()
            val localInv = securePrefs.stickerInventory
            val lastSynced = securePrefs.lastSyncedInventory
            val shouldPull = localInv.isBlank() || localInv == lastSynced || !securePrefs.hasSyncedStickers
            if (shouldPull) {
                val serverAsset = apiService.getStickerInventory()
                securePrefs.stickerInventory = serverAsset.sticker_inventory
                securePrefs.eggEnergy = serverAsset.egg_energy
                securePrefs.lastSyncedInventory = serverAsset.sticker_inventory
            }
            securePrefs.hasSyncedStickers = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull sticker inventory: ${e.message}", e)
        }
    }

    suspend fun syncStickerInventoryUp() = withContext(Dispatchers.IO) {
        try {
            syncStickerConfigDown()
            val serverAsset = apiService.getStickerInventory()
            securePrefs.stickerInventory = serverAsset.sticker_inventory
            securePrefs.eggEnergy = serverAsset.egg_energy
            securePrefs.lastSyncedInventory = serverAsset.sticker_inventory
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync sticker inventory down: ${e.message}", e)
        }
    }
}
