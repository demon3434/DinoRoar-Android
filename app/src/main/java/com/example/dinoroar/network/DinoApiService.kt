package com.example.dinoroar.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String
)

@Serializable
data class UserResponse(
    val id: Int,
    val username: String,
    val nickname: String? = null,
    val is_admin: Boolean,
    val lock_pattern: String,
    val lock_reset_flag: String,
    val theme: String? = null
)

@Serializable
data class UserUpdateLock(
    val lock_pattern: String
)

@Serializable
data class LogCreate(
    val uuid: String,
    val title: String? = null,
    val incident_date: String, // ISO 8601 UTC
    val mood_dino_id: Int,
    val content: String,
    val own_thoughts: String? = null,
    val updated_at: String,
    val version: Int,
    val person_uuids: List<String> = emptyList(),
    val canvas_instance_id: Int? = null,
    val canvas_aspect_ratio: String = "2:1"
)

@Serializable
data class LogSyncPayload(
    val logs: List<LogCreate>,
    val deleted_uuids: List<String>
)

@Serializable
data class AttachmentResponse(
    val uuid: String,
    val file_name: String,
    val mime_type: String,
    val file_size: Long,
    val created_at: String,
    val title: String? = null
)

@Serializable
data class LogResponse(
    val id: Int,
    val uuid: String,
    val title: String? = null,
    val incident_date: String,
    val mood_dino_id: Int,
    val content: String,
    val own_thoughts: String? = null,
    val created_at: String,
    val updated_at: String,
    val is_deleted: Boolean,
    val version: Int = 1,
    val person_uuids: List<String> = emptyList(),
    val attachments: List<AttachmentResponse> = emptyList(),
    val canvas_instance_id: Int? = null,
    val canvas_aspect_ratio: String = "2:1",
    val canvas_image_url: String? = null
)

@Serializable
data class PersonSyncItem(
    val uuid: String,
    val name: String,
    val abbreviation: String,
    val relationship: String,
    val category_uuid: String? = null,
    val sort_order: Int = 0,
    val color_tag: String? = "red",
    val is_temporary: Boolean = false,
    val created_at: String
)

@Serializable
data class PersonSyncPayload(
    val persons: List<PersonSyncItem>,
    val deleted_uuids: List<String>
)

@Serializable
data class PersonResponse(
    val uuid: String,
    val name: String,
    val abbreviation: String,
    val relationship: String,
    val category_uuid: String? = null,
    val sort_order: Int = 0,
    val color_tag: String? = "red",
    val is_temporary: Boolean = false,
    val created_at: String,
    val is_deleted: Boolean
)

@Serializable
data class PersonCategorySyncItem(
    val uuid: String,
    val name: String,
    val sort_order: Int = 0,
    val created_at: String? = null
)

@Serializable
data class PersonCategorySyncPayload(
    val categories: List<PersonCategorySyncItem>,
    val deleted_uuids: List<String>
)

@Serializable
data class PersonCategoryResponse(
    val uuid: String,
    val name: String,
    val sort_order: Int = 0,
    val created_at: String? = null,
    val is_deleted: Boolean
)

@Serializable
data class DinoConfigDto(
    val id: Int,
    val legacy_key: String,
    val name: String,
    val mood_label: String,
    val mood_tip: String? = null,
    val image_url: String,
    val mood_score: Int = 5,
    val sort_order: Int = 99,
    val is_active: Boolean = true
)

interface DinoApiService {
    @GET("api/dino/config")
    suspend fun getDinoConfig(): List<DinoConfigDto>

    @FormUrlEncoded
    @POST("api/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): TokenResponse

    @GET("api/auth/me")
    suspend fun getMe(): UserResponse

    @POST("api/auth/lock")
    suspend fun updateLockPattern(
        @Body payload: UserUpdateLock
    ): UserResponse

    @FormUrlEncoded
    @POST("api/auth/password")
    suspend fun changePassword(
        @Field("old_password") oldPass: String,
        @Field("new_password") newPass: String
    ): retrofit2.Response<Unit>

    @POST("api/logs/sync")
    suspend fun syncLogs(
        @Body payload: LogSyncPayload
    ): List<LogResponse>

    @POST("api/persons/sync")
    suspend fun syncPersons(
        @Body payload: PersonSyncPayload
    ): List<PersonResponse>

    @Multipart
    @POST("api/attachments/upload")
    suspend fun uploadAttachment(
        @Part file: MultipartBody.Part,
        @Part("uuid") uuid: RequestBody,
        @Part("log_uuid") logUuid: RequestBody?,
        @Part("title") title: RequestBody?
    ): AttachmentResponse

    @DELETE("api/attachments/{uuid}")
    suspend fun deleteAttachment(
        @Path("uuid") uuid: String
    ): Response<Unit>

    @Streaming
    @GET("api/attachments/download/{uuid}")
    suspend fun downloadAttachment(
        @Path("uuid") uuid: String
    ): okhttp3.ResponseBody

    @POST("api/attachments/check-md5")
    suspend fun checkMd5(
        @Body payload: CheckMd5Payload
    ): CheckMd5Response

    @POST("api/categories/sync")
    suspend fun syncCategories(
        @Body payload: PersonCategorySyncPayload
    ): List<PersonCategoryResponse>

    @GET("api/stickers/inventory")
    suspend fun getStickerInventory(): StickerInventorySyncDto


    @GET("api/stickers/config")
    suspend fun getStickersConfig(): List<StickerSeriesDto>

    @POST("api/stickers/exchange")
    suspend fun exchangeSticker(
        @Body payload: StickerExchangeRequest
    ): StickerInventorySyncDto

    @GET("api/canvases/inventory")
    suspend fun getCanvasInventory(): CanvasSyncPayload

    @POST("api/canvases/inventory")
    suspend fun syncCanvasInventory(
        @Body payload: CanvasSyncPayload
    ): CanvasSyncPayload

    @GET("api/canvases/config")
    suspend fun getCanvasesConfig(): List<CanvasSeriesDto>

    @POST("api/canvases/exchange")
    suspend fun exchangeCanvas(
        @Body payload: CanvasExchangeRequest
    ): CanvasSyncPayload

    @GET("api/promotions/active-summary")
    suspend fun getActivePromotionsSummary(): List<PromotionSummaryDto>

    @GET("api/checkin/status")
    suspend fun getCheckInStatus(): CheckInStatusResponse

    @POST("api/checkin")
    suspend fun performCheckIn(
        @Body request: CheckInRequest
    ): CheckInResultResponse

    @GET("api/energy/transactions")
    suspend fun getEnergyTransactions(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("filter_type") filterType: String? = null,
        @Query("time_range") timeRange: String? = null,
        @Query("month") month: String? = null
    ): EnergyTransactionPageResponse


    @Multipart
    @POST("api/stt/transcribe")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part
    ): SttTranscribeResponse
}


@Serializable
data class PromotionSummaryDto(
    val id: Int,
    val name: String,
    val description: String? = null,
    val rules_summary: List<String> = emptyList(),
    val highlight_text: String? = null,
    val start_time: String,
    val end_time: String
)


@Serializable
data class SttTranscribeResponse(
    val text: String,
    val emotion: String = "平静",
    val raw_tags: List<String> = emptyList()
)

@Serializable
data class CheckMd5Payload(
    val md5: String,
    val uuid: String,
    val log_uuid: String,
    val file_name: String,
    val mime_type: String,
    val file_size: Long,
    val title: String? = null
)

@Serializable
data class CheckMd5Response(
    val hit: Boolean,
    val attachment_uuid: String? = null
)

@Serializable
data class StickerInventorySyncDto(
    val sticker_inventory: String,
    val egg_energy: Int
)

@Serializable
data class StickerConfigDto(
    val id: Int,
    val series_id: Int? = null,
    val name: String,
    val image_url: String,
    val description: String? = null,
    val sort_order: Int,
    val exchange_price: Int,
    val original_price: Int? = null,
    val is_on_sale: Boolean = false,
    val is_active: Boolean = true,
    val is_deleted: Boolean = false,
    val created_at: String
)

@Serializable
data class StickerSeriesDto(
    val id: Int,
    val name: String,
    val sort_order: Int,
    val is_active: Boolean = true,
    val is_deleted: Boolean = false,
    val created_at: String,
    val stickers: List<StickerConfigDto> = emptyList()
)

@Serializable
data class StickerExchangeRequest(
    val sticker_id: Int
)

@Serializable
data class CanvasInstanceDto(
    val id: Int,
    val canvas_set_id: Int,
    @SerialName("aspect_ratio") val aspectRatio: String,
    @SerialName("image_url") val imageUrl: String,
    val width: Int = 1440,
    val height: Int,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class CanvasSetDto(
    val id: Int,
    val series_id: Int? = null,
    val name: String,
    val description: String? = null,
    val sort_order: Int,
    val exchange_price: Int = 50,
    val original_price: Int? = null,
    val is_on_sale: Boolean = false,
    val is_active: Boolean = true,
    val is_deleted: Boolean = false,
    val created_at: String,
    val instances: List<CanvasInstanceDto> = emptyList()
)

@Serializable
data class CanvasSeriesDto(
    val id: Int,
    val name: String,
    val sort_order: Int,
    val is_active: Boolean = true,
    val is_deleted: Boolean = false,
    val created_at: String,
    val sets: List<CanvasSetDto> = emptyList()
)

@Serializable
data class CanvasSyncPayload(
    val canvas_inventory: String = "",
    val egg_energy: Int
)

@Serializable
data class CanvasExchangeRequest(
    val canvas_set_id: Int
)






