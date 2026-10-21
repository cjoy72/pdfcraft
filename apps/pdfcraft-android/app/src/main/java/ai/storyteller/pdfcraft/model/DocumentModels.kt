package ai.storyteller.pdfcraft.model

import android.graphics.RectF

data class PageDimension(
    val width: Float,
    val height: Float,
    val rotation: Float
) {
    val aspectRatio: Float
        get() = if (height > 0f) width / height else 1f
}

data class DocumentMetadata(
    val title: String?,
    val author: String?,
    val subject: String?,
    val creator: String?,
    val producer: String?,
    val pageCount: Int,
    val pdfVersion: String,
    val isEncrypted: Boolean
)

data class OutlineItem(
    val title: String,
    val page: Int?,
    val children: List<OutlineItem> = emptyList()
)

data class SearchHit(
    val page: Int,
    val rects: List<RectF>,
    val snippet: String
)

enum class DisplayMode {
    DAY,
    NIGHT,
    SEPIA,
    EINK
}

enum class ToolMode {
    READ,
    HIGHLIGHT,
    PEN,
    ERASER
}

data class InkPoint(val x: Float, val y: Float, val pressure: Float = 1f)
data class InkStroke(val points: List<InkPoint>, val color: Int, val width: Float)
