package com.example.dinoroar.network

import java.util.Collections

object StickerConfigCache {
    private val cachedStickers = Collections.synchronizedList(mutableListOf<StickerConfigDto>())

    fun get(): List<StickerConfigDto> {
        return synchronized(cachedStickers) {
            cachedStickers.toList()
        }
    }

    fun set(stickers: List<StickerConfigDto>) {
        synchronized(cachedStickers) {
            cachedStickers.clear()
            cachedStickers.addAll(stickers)
        }
    }

    fun isEmpty(): Boolean {
        return synchronized(cachedStickers) {
            cachedStickers.isEmpty()
        }
    }

    fun clear() {
        synchronized(cachedStickers) {
            cachedStickers.clear()
        }
    }
}
