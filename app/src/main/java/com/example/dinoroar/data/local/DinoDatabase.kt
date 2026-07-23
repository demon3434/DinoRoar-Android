package com.example.dinoroar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LogEntity::class, AttachmentEntity::class, PersonEntity::class, LogPersonCrossRef::class, PersonCategoryEntity::class, DinoConfigEntity::class, StickerSeriesEntity::class, StickerEntity::class],
    version = 11,
    exportSchema = false
)
abstract class DinoDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun personDao(): PersonDao
    abstract fun logPersonDao(): LogPersonDao
    abstract fun dinoConfigDao(): DinoConfigDao
    abstract fun stickerDao(): StickerDao
    abstract fun stickerSeriesDao(): StickerSeriesDao

    companion object {
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sticker_series` (
                        `id` INTEGER NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `sortOrder` INTEGER NOT NULL DEFAULT 0, 
                        `isActive` INTEGER NOT NULL DEFAULT 1, 
                        `isDeleted` INTEGER NOT NULL DEFAULT 0, 
                        `createdAt` TEXT NOT NULL DEFAULT '', 
                        PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stickers` (
                        `id` INTEGER NOT NULL, 
                        `seriesId` INTEGER, 
                        `name` TEXT NOT NULL, 
                        `imageUrl` TEXT NOT NULL, 
                        `description` TEXT, 
                        `sortOrder` INTEGER NOT NULL DEFAULT 0, 
                        `exchangePrice` INTEGER NOT NULL DEFAULT 20, 
                        `isActive` INTEGER NOT NULL DEFAULT 1, 
                        `isDeleted` INTEGER NOT NULL DEFAULT 0, 
                        `createdAt` TEXT NOT NULL DEFAULT '', 
                        PRIMARY KEY(`id`)
                    )
                """)
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 创建 dino_config 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dino_config` (
                        `id` INTEGER NOT NULL PRIMARY KEY, 
                        `legacyKey` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `moodLabel` TEXT NOT NULL, 
                        `moodTip` TEXT, 
                        `imageUrl` TEXT NOT NULL, 
                        `moodScore` INTEGER NOT NULL DEFAULT 5, 
                        `sortOrder` INTEGER NOT NULL DEFAULT 99, 
                        `isActive` INTEGER NOT NULL DEFAULT 1
                    )
                """)
                
                // 2. 为 logs 表添加 moodDinoId 字段
                db.execSQL("ALTER TABLE logs ADD COLUMN moodDinoId INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 创建分类表 person_categories
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `person_categories` (
                        `uuid` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `sortOrder` INTEGER NOT NULL, 
                        `createdAt` TEXT NOT NULL, 
                        `isDeleted` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`uuid`)
                    )
                """)
                
                // 2. 为 persons 表增加 categoryUuid, sortOrder, colorTag, isTemporary 字段
                db.execSQL("ALTER TABLE persons ADD COLUMN categoryUuid TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE persons ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE persons ADD COLUMN colorTag TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE persons ADD COLUMN isTemporary INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE logs ADD COLUMN title TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE logs ADD COLUMN isLocalOnly INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE logs ADD COLUMN isConflict INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE logs ADD COLUMN serverContent TEXT")
                db.execSQL("ALTER TABLE logs ADD COLUMN serverUpdatedAt TEXT")
                db.execSQL("ALTER TABLE logs ADD COLUMN serverVersion INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE logs ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE attachments ADD COLUMN title TEXT")
                db.execSQL("ALTER TABLE attachments ADD COLUMN md5 TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 为 attachments 新增 isDeleted 字段，默认值为 0 (false)
                db.execSQL("ALTER TABLE attachments ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 重构 logs 表：创建 logs_new（移除 scolder, fireLevel, errorIndex 字段，新增 updatedAt 字段）
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `logs_new` (
                        `uuid` TEXT NOT NULL, 
                        `incidentDate` TEXT NOT NULL, 
                        `moodDino` TEXT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `ownThoughts` TEXT, 
                        `createdAt` TEXT NOT NULL, 
                        `updatedAt` TEXT NOT NULL DEFAULT '', 
                        `isDeleted` INTEGER NOT NULL DEFAULT 0, 
                        `isSynced` INTEGER NOT NULL DEFAULT 0, 
                        PRIMARY KEY(`uuid`)
                    )
                """)
                // 复制原有日志数据
                db.execSQL("""
                    INSERT INTO logs_new (uuid, incidentDate, moodDino, content, ownThoughts, createdAt, isDeleted, isSynced)
                    SELECT uuid, incidentDate, moodDino, content, ownThoughts, createdAt, isDeleted, isSynced FROM logs
                """)
                // 删除旧 logs 表并重命名新表
                db.execSQL("DROP TABLE logs")
                db.execSQL("ALTER TABLE logs_new RENAME TO logs")

                // 2. 创建 persons 人物表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `persons` (
                        `uuid` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `abbreviation` TEXT NOT NULL, 
                        `relationship` TEXT NOT NULL, 
                        `createdAt` TEXT NOT NULL, 
                        `isDeleted` INTEGER NOT NULL DEFAULT 0, 
                        `isSynced` INTEGER NOT NULL DEFAULT 0, 
                        PRIMARY KEY(`uuid`)
                    )
                """)

                // 3. 创建多对多交叉联接表 log_person_cross_ref
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `log_person_cross_ref` (
                        `logUuid` TEXT NOT NULL, 
                        `personUuid` TEXT NOT NULL, 
                        PRIMARY KEY(`logUuid`, `personUuid`)
                    )
                """)
            }
        }
    }
}
