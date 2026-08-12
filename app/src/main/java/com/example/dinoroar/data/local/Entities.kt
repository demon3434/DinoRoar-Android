package com.example.dinoroar.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.ForeignKey

@Entity(tableName = "logs", indices = [Index(value = ["userId"])])
data class LogEntity(
    @PrimaryKey val uuid: String,
    val userId: String = "", // 绑定关联的账号 Username/ID
    val title: String? = null,
    val incidentDate: String, // ISO-8601 String
    val moodDinoId: Int, // 关联 dino_config 的外键 ID
    val content: String,
    val ownThoughts: String?,
    val createdAt: String,
    val updatedAt: String,    // 新增修改时间戳 (ISO-8601 格式)
    val isDeleted: Boolean = false,
    val isSynced: Boolean = false,
    val version: Int = 1,
    val isConflict: Boolean = false,
    val serverContent: String? = null,
    val serverUpdatedAt: String? = null,
    val serverVersion: Int = 0,
    val isLocalOnly: Boolean = false
)

data class LogWithConfig(
    @Embedded val log: LogEntity,
    @Relation(
        parentColumn = "moodDinoId",
        entityColumn = "id"
    )
    val dinoConfig: DinoConfigEntity?
)


@Entity(tableName = "dino_config")
data class DinoConfigEntity(
    @PrimaryKey val id: Int,
    val legacyKey: String,
    val name: String,
    val moodLabel: String,
    val moodTip: String? = null,
    val imageUrl: String,
    val moodScore: Int = 5,
    val sortOrder: Int = 99,
    val isActive: Boolean = true
)

@Entity(tableName = "attachments", indices = [Index(value = ["userId"])])
data class AttachmentEntity(
    @PrimaryKey val uuid: String,
    val userId: String = "",
    val logUuid: String?,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val localFilePath: String?,
    val remoteUrl: String?,
    val createdAt: String,
    val isDeleted: Boolean = false,
    val isSynced: Boolean = false,
    val title: String? = null,
    val md5: String? = null
)

@Entity(tableName = "person_categories", indices = [Index(value = ["userId"])])
data class PersonCategoryEntity(
    @PrimaryKey val uuid: String,
    val userId: String = "",
    val name: String,
    val sortOrder: Int,
    val createdAt: String,
    val isDeleted: Boolean = false
)

@Entity(tableName = "persons", indices = [Index(value = ["userId"])])
data class PersonEntity(
    @PrimaryKey val uuid: String,
    val userId: String = "",
    val name: String,
    val abbreviation: String,   // 快速拼音缩写 (例如: "XM" -> 小明)
    val relationship: String,   // 关系 (例如: 爸爸, 老师, 同桌)
    val categoryUuid: String? = null,
    val sortOrder: Int = 0,
    val colorTag: String? = null,
    val isTemporary: Boolean = false,
    val createdAt: String,
    val isDeleted: Boolean = false,
    val isSynced: Boolean = false
)

@Entity(
    tableName = "log_person_cross_ref",
    primaryKeys = ["logUuid", "personUuid"],
    indices = [Index(value = ["logUuid"]), Index(value = ["personUuid"]), Index(value = ["userId"])]
)
data class LogPersonCrossRef(
    val logUuid: String,
    val personUuid: String,
    val userId: String = ""
)

@Entity(tableName = "sticker_series")
data class StickerSeriesEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: String = ""
)

@Entity(tableName = "stickers")
data class StickerEntity(
    @PrimaryKey val id: Int,
    val seriesId: Int? = null,
    val name: String,
    val imageUrl: String,
    val description: String? = null,
    val sortOrder: Int = 0,
    val exchangePrice: Int = 20,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: String = ""
)


@Entity(tableName = "canvas_series")
data class CanvasSeriesEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: String = ""
)

@Entity(
    tableName = "canvas_sets",
    foreignKeys = [
        ForeignKey(
            entity = CanvasSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["series_id"])]
)
data class CanvasSetEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "series_id") val seriesId: Int?,
    val name: String,
    val description: String?,
    val sortOrder: Int = 0,
    val exchangePrice: Int = 50,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: String = ""
)

@Entity(
    tableName = "canvas_instances",
    indices = [Index(value = ["canvas_set_id"])]
)
data class CanvasInstanceEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "canvas_set_id") val canvasSetId: Int,
    val aspectRatio: String, // "16:9", "4:3", "1:1", "2:1"
    val imageUrl: String,
    val width: Int = 1440,
    val height: Int,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: String = ""
)

@Entity(
    tableName = "log_canvases",
    foreignKeys = [
        ForeignKey(
            entity = LogEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["log_uuid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["canvas_instance_id"])]
)
data class LogCanvasEntity(
    @PrimaryKey @ColumnInfo(name = "log_uuid") val logUuid: String,
    @ColumnInfo(name = "canvas_instance_id") val canvasInstanceId: Int?,
    @ColumnInfo(name = "canvas_aspect_ratio") val canvasAspectRatio: String // "16:9", "4:3", "1:1", "2:1"
)


