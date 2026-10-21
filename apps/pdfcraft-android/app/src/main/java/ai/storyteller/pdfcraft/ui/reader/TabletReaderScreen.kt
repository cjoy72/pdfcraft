package ai.storyteller.pdfcraft.ui.reader

import ai.storyteller.pdfcraft.core.PdfDocument
import ai.storyteller.pdfcraft.core.RenderPool
import ai.storyteller.pdfcraft.model.DisplayMode
import ai.storyteller.pdfcraft.model.InkStroke
import ai.storyteller.pdfcraft.model.ToolMode
import ai.storyteller.pdfcraft.ui.components.OutlineDrawer
import ai.storyteller.pdfcraft.ui.theme.getColorsForDisplayMode
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class TabletSidebarTab {
    THUMBNAILS,
    OUTLINE,
    SEARCH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabletReaderScreen(
    document: PdfDocument,
    renderPool: RenderPool,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var displayMode by remember { mutableStateOf(DisplayMode.DAY) }
    var toolMode by remember { mutableStateOf(ToolMode.READ) }
    var isTwoUpSpread by remember { mutableStateOf(false) }
    var isSidebarOpen by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf(TabletSidebarTab.THUMBNAILS) }

    val activeColor = remember { mutableStateOf(Color(0xFFD32F2F)) }
    val activeStrokeWidth = remember { mutableFloatStateOf(4f) }
    val pageStrokes = remember { mutableStateMapOf<Int, MutableList<InkStroke>>() }

    val colors = getColorsForDisplayMode(displayMode)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = document.metadata?.title ?: "Document",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (isTwoUpSpread && currentPage + 1 < document.pageCount) {
                                "Pages ${currentPage + 1}-${currentPage + 2} of ${document.pageCount}"
                            } else {
                                "Page ${currentPage + 1} of ${document.pageCount}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onBackClicked) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = { isSidebarOpen = !isSidebarOpen }) {
                            Icon(
                                if (isSidebarOpen) Icons.Default.MenuOpen else Icons.Default.Menu,
                                contentDescription = "Toggle Sidebar"
                            )
                        }
                    }
                },
                actions = {
                    // Two-Up Spread Toggle
                    IconButton(onClick = { isTwoUpSpread = !isTwoUpSpread }) {
                        Icon(
                            if (isTwoUpSpread) Icons.Default.AutoStories else Icons.Default.Description,
                            contentDescription = "Two-Up Spread Mode"
                        )
                    }

                    // Display Mode Toggle
                    IconButton(onClick = {
                        displayMode = when (displayMode) {
                            DisplayMode.DAY -> DisplayMode.SEPIA
                            DisplayMode.SEPIA -> DisplayMode.NIGHT
                            DisplayMode.NIGHT -> DisplayMode.EINK
                            DisplayMode.EINK -> DisplayMode.DAY
                        }
                    }) {
                        Icon(
                            imageVector = when (displayMode) {
                                DisplayMode.DAY -> Icons.Default.WbSunny
                                DisplayMode.SEPIA -> Icons.Default.MenuBook
                                DisplayMode.NIGHT -> Icons.Default.NightlightRound
                                DisplayMode.EINK -> Icons.Default.Contrast
                            },
                            contentDescription = "Display Mode"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.toolbarBackground,
                    titleContentColor = colors.toolbarContent,
                    actionIconContentColor = colors.toolbarContent
                )
            )
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            // Adaptive Left Sidebar
            AnimatedVisibility(
                visible = isSidebarOpen,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight(),
                    color = colors.toolbarBackground,
                    tonalElevation = 2.dp
                ) {
                    Column {
                        TabRow(
                            selectedTabIndex = currentTab.ordinal,
                            containerColor = colors.toolbarBackground
                        ) {
                            Tab(
                                selected = currentTab == TabletSidebarTab.THUMBNAILS,
                                onClick = { currentTab = TabletSidebarTab.THUMBNAILS },
                                icon = { Icon(Icons.Default.GridView, contentDescription = null) },
                                text = { Text("Pages") }
                            )
                            Tab(
                                selected = currentTab == TabletSidebarTab.OUTLINE,
                                onClick = { currentTab = TabletSidebarTab.OUTLINE },
                                icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                                text = { Text("Outline") }
                            )
                        }

                        when (currentTab) {
                            TabletSidebarTab.THUMBNAILS -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(document.pageCount) { idx ->
                                        TabletThumbnailCard(
                                            pageIndex = idx,
                                            isSelected = idx == currentPage || (isTwoUpSpread && idx == currentPage + 1),
                                            renderPool = renderPool,
                                            displayMode = displayMode,
                                            onClick = { currentPage = idx }
                                        )
                                    }
                                }
                            }
                            TabletSidebarTab.OUTLINE -> {
                                OutlineDrawer(
                                    outline = document.outline,
                                    onPageSelected = { page -> currentPage = page }
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Main Document Reading & Markup Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (isTwoUpSpread && currentPage + 1 < document.pageCount) {
                    // Two-Up Spread Layout
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            PdfViewport(
                                pageIndex = currentPage,
                                renderPool = renderPool,
                                displayMode = displayMode,
                                toolMode = toolMode,
                                activeColor = activeColor.value,
                                activeStrokeWidth = activeStrokeWidth.floatValue,
                                strokes = pageStrokes[currentPage] ?: emptyList(),
                                onStrokeAdded = { stroke ->
                                    pageStrokes.getOrPut(currentPage) { mutableListOf() }.add(stroke)
                                }
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            PdfViewport(
                                pageIndex = currentPage + 1,
                                renderPool = renderPool,
                                displayMode = displayMode,
                                toolMode = toolMode,
                                activeColor = activeColor.value,
                                activeStrokeWidth = activeStrokeWidth.floatValue,
                                strokes = pageStrokes[currentPage + 1] ?: emptyList(),
                                onStrokeAdded = { stroke ->
                                    pageStrokes.getOrPut(currentPage + 1) { mutableListOf() }.add(stroke)
                                }
                            )
                        }
                    }
                } else {
                    // Single Page Viewport
                    PdfViewport(
                        pageIndex = currentPage,
                        renderPool = renderPool,
                        displayMode = displayMode,
                        toolMode = toolMode,
                        activeColor = activeColor.value,
                        activeStrokeWidth = activeStrokeWidth.floatValue,
                        strokes = pageStrokes[currentPage] ?: emptyList(),
                        onStrokeAdded = { stroke ->
                            pageStrokes.getOrPut(currentPage) { mutableListOf() }.add(stroke)
                        }
                    )
                }

                // Previous / Next Page overlay navigation buttons
                if (currentPage > 0) {
                    IconButton(
                        onClick = {
                            currentPage = if (isTwoUpSpread) (currentPage - 2).coerceAtLeast(0) else currentPage - 1
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                    }
                }

                if (currentPage < document.pageCount - 1) {
                    IconButton(
                        onClick = {
                            currentPage = if (isTwoUpSpread) (currentPage + 2).coerceAtMost(document.pageCount - 1) else currentPage + 1
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                    }
                }

                // Dockable Stylus / Markup Palette (Floating overlay for tablets)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = colors.toolbarBackground.copy(alpha = 0.95f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = toolMode == ToolMode.READ,
                            onClick = { toolMode = ToolMode.READ },
                            label = { Text("Read") },
                            leadingIcon = { Icon(Icons.Default.TouchApp, contentDescription = null) }
                        )
                        FilterChip(
                            selected = toolMode == ToolMode.PEN,
                            onClick = { toolMode = ToolMode.PEN },
                            label = { Text("Stylus Pen") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        FilterChip(
                            selected = toolMode == ToolMode.HIGHLIGHT,
                            onClick = { toolMode = ToolMode.HIGHLIGHT },
                            label = { Text("Highlighter") },
                            leadingIcon = { Icon(Icons.Default.BorderColor, contentDescription = null) }
                        )

                        // Pen Colors
                        val colorsList = listOf(Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFFBC02D), Color(0xFF212121))
                        for (c in colorsList) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { activeColor.value = c }
                                    .then(
                                        if (activeColor.value == c) Modifier.border(2.dp, Color.White, CircleShape) else Modifier
                                    )
                            )
                        }

                        IconButton(onClick = {
                            pageStrokes[currentPage]?.clear()
                            if (isTwoUpSpread) pageStrokes[currentPage + 1]?.clear()
                        }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Ink")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletThumbnailCard(
    pageIndex: Int,
    isSelected: Boolean,
    renderPool: RenderPool,
    displayMode: DisplayMode,
    onClick: () -> Unit
) {
    var bmp by remember(pageIndex, displayMode) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(pageIndex, displayMode) {
        bmp = renderPool.renderPage(pageIndex, zoom = 0.35f, mode = displayMode)
    }

    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            val bitmap = bmp
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Page ${pageIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black
            )
        }
    }
}
