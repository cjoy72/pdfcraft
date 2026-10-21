package ai.storyteller.pdfcraft.core

import ai.storyteller.pdfcraft.model.DocumentMetadata
import ai.storyteller.pdfcraft.model.OutlineItem
import ai.storyteller.pdfcraft.model.PageDimension
import ai.storyteller.pdfcraft.model.SearchHit
import android.graphics.Bitmap
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class PdfDocument private constructor(private var handle: Long) : Closeable {

    private val isClosed = AtomicBoolean(false)
    val pageCount: Int
    val metadata: DocumentMetadata?
    val outline: List<OutlineItem>

    init {
        pageCount = PdfEngine.nativeGetPageCount(handle)
        metadata = parseMetadata(PdfEngine.nativeGetMetadata(handle))
        outline = parseOutline(PdfEngine.nativeGetOutline(handle))
    }

    fun getPageDimension(pageIndex: Int): PageDimension {
        ensureOpen()
        val dims = PdfEngine.nativeGetPageDimensions(handle, pageIndex)
        return PageDimension(dims[0], dims[1], dims[2])
    }

    fun renderPage(
        pageIndex: Int,
        zoom: Float,
        bitmap: Bitmap,
        darkMode: Boolean = false,
        sepiaMode: Boolean = false
    ): Boolean {
        ensureOpen()
        return PdfEngine.nativeRenderPageToBitmap(
            handle,
            pageIndex,
            zoom,
            bitmap,
            darkMode,
            sepiaMode
        )
    }

    fun search(query: String): List<SearchHit> {
        ensureOpen()
        val jsonStr = PdfEngine.nativeSearchText(handle, query) ?: return emptyList()
        val list = mutableListOf<SearchHit>()
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val page = obj.getInt("page")
            val snippet = obj.getString("snippet")
            val rectsArr = obj.getJSONArray("rects")
            val rects = mutableListOf<RectF>()
            for (j in 0 until rectsArr.length()) {
                val r = rectsArr.getJSONArray(j)
                rects.add(RectF(r.getDouble(0).toFloat(), r.getDouble(1).toFloat(), r.getDouble(2).toFloat(), r.getDouble(3).toFloat()))
            }
            list.add(SearchHit(page, rects, snippet))
        }
        return list
    }

    private fun parseMetadata(jsonStr: String?): DocumentMetadata? {
        if (jsonStr == null) return null
        return try {
            val obj = JSONObject(jsonStr)
            DocumentMetadata(
                title = obj.optString("title").takeIf { it.isNotEmpty() },
                author = obj.optString("author").takeIf { it.isNotEmpty() },
                subject = obj.optString("subject").takeIf { it.isNotEmpty() },
                creator = obj.optString("creator").takeIf { it.isNotEmpty() },
                producer = obj.optString("producer").takeIf { it.isNotEmpty() },
                pageCount = obj.getInt("page_count"),
                pdfVersion = obj.getString("pdf_version"),
                isEncrypted = obj.getBoolean("encrypted")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseOutline(jsonStr: String?): List<OutlineItem> {
        if (jsonStr == null) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            parseOutlineArray(arr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseOutlineArray(arr: JSONArray): List<OutlineItem> {
        val items = mutableListOf<OutlineItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val title = obj.getString("title")
            val page = if (obj.has("page") && !obj.isNull("page")) obj.getInt("page") else null
            val children = if (obj.has("children")) parseOutlineArray(obj.getJSONArray("children")) else emptyList()
            items.add(OutlineItem(title, page, children))
        }
        return items
    }

    private fun ensureOpen() {
        if (isClosed.get() || handle == 0L) {
            throw IllegalStateException("Document has been closed")
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            val h = handle
            handle = 0L
            if (h != 0L) {
                PdfEngine.nativeCloseDocument(h)
            }
        }
    }

    companion object {
        fun fromBytes(bytes: ByteArray, password: String? = null): PdfDocument {
            val handle = PdfEngine.nativeOpenDocumentBytes(bytes, password)
            if (handle == 0L) {
                throw RuntimeException("Could not open PDF from bytes")
            }
            return PdfDocument(handle)
        }

        fun fromFd(fd: Int, password: String? = null): PdfDocument {
            val handle = PdfEngine.nativeOpenDocumentFd(fd, password)
            if (handle == 0L) {
                throw RuntimeException("Could not open PDF from file descriptor")
            }
            return PdfDocument(handle)
        }
    }
}
