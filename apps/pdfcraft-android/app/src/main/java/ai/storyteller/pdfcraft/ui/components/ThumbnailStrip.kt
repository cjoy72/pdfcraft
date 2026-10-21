package ai.storyteller.pdfcraft.ui.components

import ai.storyteller.pdfcraft.core.RenderPool
import ai.storyteller.pdfcraft.model.DisplayMode
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun ThumbnailStrip(
    pageCount: Int,
    currentPage: Int,
    renderPool: RenderPool,
    displayMode: DisplayMode,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(pageCount) { pageIndex ->
            ThumbnailItem(
                pageIndex = pageIndex,
                isSelected = pageIndex == currentPage,
                renderPool = renderPool,
                displayMode = displayMode,
                onClick = { onPageSelected(pageIndex) }
            )
        }
    }
}

@Composable
private fun ThumbnailItem(
    pageIndex: Int,
    isSelected: Boolean,
    renderPool: RenderPool,
    displayMode: DisplayMode,
    onClick: () -> Unit
) {
    var thumbnailBitmap by remember(pageIndex, displayMode) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, displayMode) {
        thumbnailBitmap = renderPool.renderPage(pageIndex, zoom = 0.2f, mode = displayMode)
    }

    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(75.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(2.dp, borderColor, RoundedCornerShape(4.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbnailBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = "${pageIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
