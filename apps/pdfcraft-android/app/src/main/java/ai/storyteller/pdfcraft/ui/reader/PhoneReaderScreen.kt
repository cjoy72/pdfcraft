package ai.storyteller.pdfcraft.ui.reader

import ai.storyteller.pdfcraft.core.PdfDocument
import ai.storyteller.pdfcraft.core.RenderPool
import ai.storyteller.pdfcraft.model.DisplayMode
import ai.storyteller.pdfcraft.model.InkStroke
import ai.storyteller.pdfcraft.model.ToolMode
import ai.storyteller.pdfcraft.ui.components.OutlineDrawer
import ai.storyteller.pdfcraft.ui.components.ThumbnailStrip
import ai.storyteller.pdfcraft.ui.theme.getColorsForDisplayMode
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneReaderScreen(
    document: PdfDocument,
    renderPool: RenderPool,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var displayMode by remember { mutableStateOf(DisplayMode.DAY) }
    var toolMode by remember { mutableStateOf(ToolMode.READ) }
    var showThumbnails by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }

    val activeColor = remember { mutableStateOf(Color(0xFFD32F2F)) }
    val activeStrokeWidth = remember { mutableFloatStateOf(4f) }
    val pageStrokes = remember { mutableStateMapOf<Int, MutableList<InkStroke>>() }

    val colors = getColorsForDisplayMode(displayMode)
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document.metadata?.title ?: "Document",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Page ${currentPage + 1} of ${document.pageCount}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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

                    // Thumbnails Toggle
                    IconButton(onClick = { showThumbnails = !showThumbnails }) {
                        Icon(Icons.Default.GridView, contentDescription = "Thumbnails")
                    }

                    // Bookmarks Drawer
                    IconButton(onClick = { showOutlineSheet = true }) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.toolbarBackground,
                    titleContentColor = colors.toolbarContent,
                    actionIconContentColor = colors.toolbarContent
                )
            )
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = showThumbnails,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    ThumbnailStrip(
                        pageCount = document.pageCount,
                        currentPage = currentPage,
                        renderPool = renderPool,
                        displayMode = displayMode,
                        onPageSelected = {
                            currentPage = it
                        }
                    )
                }

                NavigationBar(
                    containerColor = colors.toolbarBackground,
                    contentColor = colors.toolbarContent
                ) {
                    NavigationBarItem(
                        selected = toolMode == ToolMode.READ,
                        onClick = { toolMode = ToolMode.READ },
                        icon = { Icon(Icons.Default.TouchApp, contentDescription = "Read") },
                        label = { Text("Read") }
                    )
                    NavigationBarItem(
                        selected = toolMode == ToolMode.PEN,
                        onClick = { toolMode = ToolMode.PEN },
                        icon = { Icon(Icons.Default.Edit, contentDescription = "Pen") },
                        label = { Text("Pen") }
                    )
                    NavigationBarItem(
                        selected = toolMode == ToolMode.HIGHLIGHT,
                        onClick = { toolMode = ToolMode.HIGHLIGHT },
                        icon = { Icon(Icons.Default.BorderColor, contentDescription = "Highlight") },
                        label = { Text("Highlight") }
                    )
                    NavigationBarItem(
                        selected = toolMode == ToolMode.ERASER,
                        onClick = {
                            toolMode = ToolMode.ERASER
                            pageStrokes[currentPage]?.clear()
                        },
                        icon = { Icon(Icons.Default.DeleteOutline, contentDescription = "Clear") },
                        label = { Text("Clear Ink") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            PdfViewport(
                pageIndex = currentPage,
                renderPool = renderPool,
                displayMode = displayMode,
                toolMode = toolMode,
                activeColor = activeColor.value,
                activeStrokeWidth = activeStrokeWidth.floatValue,
                strokes = pageStrokes[currentPage] ?: emptyList(),
                onStrokeAdded = { stroke ->
                    val list = pageStrokes.getOrPut(currentPage) { mutableListOf() }
                    list.add(stroke)
                }
            )

            // Previous / Next Page overlay navigation buttons
            if (currentPage > 0) {
                IconButton(
                    onClick = { currentPage-- },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                }
            }

            if (currentPage < document.pageCount - 1) {
                IconButton(
                    onClick = { currentPage++ },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                }
            }
        }

        if (showOutlineSheet) {
            ModalBottomSheet(
                onDismissRequest = { showOutlineSheet = false },
                sheetState = sheetState
            ) {
                OutlineDrawer(
                    outline = document.outline,
                    onPageSelected = { page ->
                        currentPage = page
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showOutlineSheet = false
                        }
                    }
                )
            }
        }
    }
}
