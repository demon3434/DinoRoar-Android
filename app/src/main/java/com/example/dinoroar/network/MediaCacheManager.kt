package com.example.dinoroar.network

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
object MediaCacheManager {
    private const val CACHE_DIR_NAME = "dino_media_cache"
    private const val MAX_CACHE_SIZE = 500 * 1024 * 1024L // 500 MB

    private var simpleCache: SimpleCache? = null

    // 声明专用于媒体流的 OkHttpClient 实例，强制采用 HTTP/1.1，以便在 Seek 中断旧请求时瞬间关闭 TCP Sockets 丢弃管道中积压的旧数据，消除 5~6 秒的 HTTP/2 多路复用解包延迟
    private val okHttpClient: OkHttpClient by lazy {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequestsPerHost = 20
            maxRequests = 64
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
            simpleCache = try {
                // 调用 6 参数构造方法，明确传入 null 作为 databaseProvider，以使用 legacy index 并避开 SQLite 写入瓶颈
                SimpleCache(cacheDir, evictor, null, null, false, true)
            } catch (e: Exception) {
                // 当 legacy 索引文件（.exi）因进程强杀导致损坏时，SimpleCache 初始化会抛出异常。
                // 自动清除整个缓存目录并重新创建，防止 Seek 后播放器永久挂起无法恢复。
                android.util.Log.w("MediaCacheManager", "SimpleCache init failed (corrupt index?), clearing cache dir and retrying", e)
                try { cacheDir.deleteRecursively() } catch (ignored: Exception) {}
                SimpleCache(cacheDir, evictor, null, null, false, true)
            }
        }
        return simpleCache!!
    }

    fun createCacheDataSourceFactory(context: Context, token: String?): CacheDataSource.Factory {
        val cache = getCache(context)

        val transferListener = object : androidx.media3.datasource.TransferListener {
            override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
                com.example.dinoroar.media.DiagnosticLogger.log("MediaTransfer", "I", "Init: offset=${dataSpec.position}, length=${dataSpec.length}, isNetwork=$isNetwork")
            }

            override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
                com.example.dinoroar.media.DiagnosticLogger.log("MediaTransfer", "I", "START: offset=${dataSpec.position}, length=${dataSpec.length}, isNetwork=$isNetwork")
            }

            override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, bytesTransferred: Int) {}

            override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
                com.example.dinoroar.media.DiagnosticLogger.log("MediaTransfer", "I", "END: offset=${dataSpec.position}, isNetwork=$isNetwork")
            }
        }
        
        // 使用 OkHttpDataSource.Factory 代替 DefaultHttpDataSource，以实现 HTTP/2 连接多路复用与连接池复用
        val httpFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient).apply {
            if (!token.isNullOrEmpty()) {
                setDefaultRequestProperties(mapOf(
                    "Authorization" to "Bearer $token"
                ))
            }
            setTransferListener(transferListener)
        }
        // 用 DefaultDataSource.Factory 包裹，支持 file:// 本地文件及其他全协议
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory).apply {
            setTransferListener(transferListener)
        }

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
