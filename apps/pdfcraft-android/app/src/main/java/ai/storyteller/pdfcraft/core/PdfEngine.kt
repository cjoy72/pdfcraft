package ai.storyteller.pdfcraft.core

import android.graphics.Bitmap

object PdfEngine {
    init {
        try {
            System.loadLibrary("rust_pdfcraft")
            nativeInitLogging()
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    external fun nativeInitLogging()

    external fun nativeOpenDocumentBytes(bytes: ByteArray, password: String?): Long

    external fun nativeOpenDocumentFd(fd: Int, password: String?): Long

    external fun nativeCloseDocument(handle: Long)

    external fun nativeGetPageCount(handle: Long): Int

    external fun nativeGetPageDimensions(handle: Long, pageIndex: Int): FloatArray

    external fun nativeGetMetadata(handle: Long): String?

    external fun nativeGetOutline(handle: Long): String?

    external fun nativeRenderPageToBitmap(
        handle: Long,
        pageIndex: Int,
        zoom: Float,
        bitmap: Bitmap,
        darkMode: Boolean,
        sepiaMode: Boolean
    ): Boolean

    external fun nativeSearchText(handle: Long, query: String): String?
}
