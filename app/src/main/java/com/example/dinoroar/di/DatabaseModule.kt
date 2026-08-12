package com.example.dinoroar.di

import android.content.Context
import androidx.room.Room
import com.example.dinoroar.data.local.AttachmentDao
import com.example.dinoroar.data.local.DinoDatabase
import com.example.dinoroar.data.local.LogDao
import com.example.dinoroar.data.local.PersonDao
import com.example.dinoroar.data.local.LogPersonDao
import com.example.dinoroar.data.local.DinoConfigDao
import com.example.dinoroar.data.local.CanvasSeriesDao
import com.example.dinoroar.data.local.CanvasSetDao
import com.example.dinoroar.data.local.CanvasInstanceDao
import com.example.dinoroar.data.local.LogCanvasDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): DinoDatabase {
        return Room.databaseBuilder(
            context,
            DinoDatabase::class.java,
            "dinoroar.db"
        )
        .addMigrations(
            DinoDatabase.MIGRATION_1_2,
            DinoDatabase.MIGRATION_2_3,
            DinoDatabase.MIGRATION_3_4,
            DinoDatabase.MIGRATION_4_5,
            DinoDatabase.MIGRATION_5_6,
            DinoDatabase.MIGRATION_6_7,
            DinoDatabase.MIGRATION_7_8,
            DinoDatabase.MIGRATION_8_9,
            DinoDatabase.MIGRATION_10_11,
            DinoDatabase.MIGRATION_11_12,
            DinoDatabase.MIGRATION_13_14,
            DinoDatabase.MIGRATION_14_15
        )
        .addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onCreate(db)
                try {
                    val jsonString = context.assets.open("dinos_init.json").bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(jsonString)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.getInt("id")
                        val legacyKey = obj.getString("legacyKey")
                        val name = obj.getString("name")
                        val moodLabel = obj.getString("moodLabel")
                        val moodTip = obj.optString("moodTip", "")
                        val imageUrl = obj.getString("imageUrl")
                        val moodScore = obj.getInt("moodScore")
                        val sortOrder = obj.getInt("sortOrder")
                        val isActive = obj.getInt("isActive")
                        
                        db.execSQL(
                            "INSERT INTO dino_config (id, legacyKey, name, moodLabel, moodTip, imageUrl, moodScore, sortOrder, isActive) " +
                            "VALUES ($id, '$legacyKey', '$name', '$moodLabel', '$moodTip', '$imageUrl', $moodScore, $sortOrder, $isActive)"
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideLogDao(database: DinoDatabase): LogDao {
        return database.logDao()
    }

    @Provides
    fun provideAttachmentDao(database: DinoDatabase): AttachmentDao {
        return database.attachmentDao()
    }

    @Provides
    fun providePersonDao(database: DinoDatabase): PersonDao {
        return database.personDao()
    }

    @Provides
    fun provideLogPersonDao(database: DinoDatabase): LogPersonDao {
        return database.logPersonDao()
    }

    @Provides
    @Singleton
    fun provideDinoConfigDao(database: DinoDatabase): DinoConfigDao {
        return database.dinoConfigDao()
    }

    @Provides
    @Singleton
    fun provideStickerDao(database: DinoDatabase): com.example.dinoroar.data.local.StickerDao {
        return database.stickerDao()
    }

    @Provides
    @Singleton
    fun provideStickerSeriesDao(database: DinoDatabase): com.example.dinoroar.data.local.StickerSeriesDao {
        return database.stickerSeriesDao()
    }

    @Provides
    @Singleton
    fun provideCanvasSeriesDao(database: DinoDatabase): CanvasSeriesDao {
        return database.canvasSeriesDao()
    }

    @Provides
    @Singleton
    fun provideCanvasSetDao(database: DinoDatabase): CanvasSetDao {
        return database.canvasSetDao()
    }

    @Provides
    @Singleton
    fun provideCanvasInstanceDao(database: DinoDatabase): CanvasInstanceDao {
        return database.canvasInstanceDao()
    }

    @Provides
    @Singleton
    fun provideLogCanvasDao(database: DinoDatabase): LogCanvasDao {
        return database.logCanvasDao()
    }
}
