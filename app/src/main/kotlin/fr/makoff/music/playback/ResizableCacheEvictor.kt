package fr.makoff.music.playback

import androidx.media3.common.C
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/** Bounds the streaming cache without touching saved offline audio. */
class ResizableCacheEvictor(initialLimit: Long) : CacheEvictor {
    private var limit = initialLimit.coerceAtLeast(0)
    private var bytes = 0L
    private val spans = TreeSet<CacheSpan>(compareBy<CacheSpan> { it.lastTouchTimestamp }.thenBy { it.key }.thenBy { it.position })
    fun resize(cache: Cache, maxBytes: Long) = synchronized(cache) {
        limit = maxBytes.coerceAtLeast(0)
        trim(cache, 0)
    }
    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() = Unit
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) trim(cache, length)
    }
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        if (spans.add(span)) bytes += span.length
        trim(cache, 0)
    }
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (spans.remove(span)) bytes -= span.length
    }
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }
    private fun trim(cache: Cache, incoming: Long) {
        while (spans.isNotEmpty() && (incoming > limit || bytes > limit - incoming)) cache.removeSpan(spans.first())
    }
}
