package ai.storyteller.pdfcraft.ui.components

import ai.storyteller.pdfcraft.model.OutlineItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OutlineDrawer(
    outline: List<OutlineItem>,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Table of Contents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (outline.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No bookmarks available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    items(outline) { item ->
                        OutlineRow(item = item, depth = 0, onPageSelected = onPageSelected)
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineRow(
    item: OutlineItem,
    depth: Int,
    onPageSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                item.page?.let { onPageSelected(it) }
                if (item.children.isNotEmpty()) {
                    expanded = !expanded
                }
            }
            .padding(start = (depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.children.isNotEmpty()) {
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )

        item.page?.let {
            Text(
                text = "${it + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (expanded && item.children.isNotEmpty()) {
        for (child in item.children) {
            OutlineRow(item = child, depth = depth + 1, onPageSelected = onPageSelected)
        }
    }
}
