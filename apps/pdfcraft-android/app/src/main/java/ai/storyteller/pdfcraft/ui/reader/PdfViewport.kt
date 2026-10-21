package ai.storyteller.pdfcraft.ui.reader

import ai.storyteller.pdfcraft.core.RenderPool
import ai.storyteller.pdfcraft.model.DisplayMode
import ai.storyteller.pdfcraft.model.InkStroke
import ai.storyteller.pdfcraft.model.ToolMode
import ai.storyteller.pdfcraft.ui.ink.StylusCanvas
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun PdfViewport(
    pageIndex: Int,
    renderPool: RenderPool,
    displayMode: DisplayMode,
    toolMode: ToolMode,
    activeColor: Color,
    activeStrokeWidth: Float,
    strokes: List<InkStroke>,
    onStrokeAdded: (InkStroke) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var pageBitmap by remember(pageIndex, displayMode) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pageIndex, displayMode) {
        isLoading = true
        pageBitmap = renderPool.renderPage(pageIndex, zoom = 1.5f, mode = displayMode)
        isLoading = false
    }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        if (toolMode == ToolMode.READ) {
            scale = (scale * zoomChange).coerceIn(0.7f, 5.0f)
            offset = if (scale > 1f) {
                Offset(offset.x + offsetChange.x, offset.y + offsetChange.y)
            } else {
                Offset.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(toolMode) {
                if (toolMode == ToolMode.READ) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1.2f) 1.0f else 2.5f
                            if (scale == 1.0f) offset = Offset.Zero
                        }
                    )
                }
            }
            .transformable(state = state)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ),
        contentAlignment = Alignment.Center
    ) {
        val bmp = pageBitmap
        if (bmp != null) {
            Box(modifier = Modifier.wrapContentSize()) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()),
                    contentScale = ContentScale.Fit
                )

                StylusCanvas(
                    toolMode = toolMode,
                    strokeColor = activeColor,
                    baseStrokeWidth = activeStrokeWidth,
                    strokes = strokes,
                    onStrokeFinished = onStrokeAdded,
                    modifier = Modifier.matchParentSize()
                )
            }
        } else if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
