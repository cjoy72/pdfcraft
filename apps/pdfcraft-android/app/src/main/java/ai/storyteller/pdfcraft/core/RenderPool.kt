package ai.storyteller.pdfcraft.core

import ai.storyteller.pdfcraft.model.DisplayMode
import ai.storyteller.pdfcraft.model.PageDimension
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RenderPool(
    private val document: PdfDocument,
    maxMemoryBytes: Int = (Runtime.getRuntime().maxMemory() / 4).toInt()
) {
    private val bitmapCache = object : LruCache<String, Bitmap>(maxMemoryBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private fun cacheKey(pageIndex: Int, zoom: Float, mode: DisplayMode): String {
        return "$pageIndex@${(zoom * 100).toInt()}_$mode"
    }

    fun getCachedPage(pageIndex: Int, zoom: Float, mode: DisplayMode): Bitmap? {
        return bitmapCache.get(cacheKey(pageIndex, zoom, mode))
    }

    suspend fun renderPage(
        pageIndex: Int,
        zoom: Float,
        mode: DisplayMode = DisplayMode.DAY
    ): Bitmap? = withContext(Dispatchers.Default) {
        val key = cacheKey(pageIndex, zoom, mode)
        val existing = bitmapCache.get(key)
        if (existing != null) return@withContext existing

        val dim = document.getPageDimension(pageIndex)
        val targetWidth = (dim.width * zoom).toInt().coerceAtLeast(1)
        val targetHeight = (dim.height * zoom).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val darkMode = mode == DisplayMode.NIGHT
        val sepiaMode = mode == DisplayMode.SEPIA

        val success = document.renderPage(
            pageIndex = pageIndex,
            zoom = zoom,
            bitmap = bitmap,
            darkMode = darkMode,
            sepiaMode = sepiaMode
        )

        if (success) {
            bitmapCache.put(key, bitmap)
            bitmap
        } else {
            bitmap.recycle()
            null
        }
    }

    fun clearCache() {
        bitmapCache.evictAll()
    }
}
