package ai.storyteller.pdfcraft.ui

import ai.storyteller.pdfcraft.core.PdfDocument
import ai.storyteller.pdfcraft.core.RenderPool
import ai.storyteller.pdfcraft.ui.reader.PhoneReaderScreen
import ai.storyteller.pdfcraft.ui.reader.TabletReaderScreen
import ai.storyteller.pdfcraft.ui.theme.PdfCraftTheme
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var currentDocument by mutableStateOf<PdfDocument?>(null)
    private var renderPool by mutableStateOf<RenderPool?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            PdfCraftTheme {
                val configuration = LocalConfiguration.current
                val isTablet = configuration.screenWidthDp >= 600

                val openDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let { openUri(it) }
                }

                val doc = currentDocument
                val pool = renderPool

                if (doc != null && pool != null) {
                    if (isTablet) {
                        TabletReaderScreen(
                            document = doc,
                            renderPool = pool,
                            onBackClicked = {
                                closeCurrentDocument()
                            }
                        )
                    } else {
                        PhoneReaderScreen(
                            document = doc,
                            renderPool = pool,
                            onBackClicked = {
                                closeCurrentDocument()
                            }
                        )
                    }
                } else {
                    EmptyStateScreen(
                        onOpenClicked = {
                            openDocumentLauncher.launch(arrayOf("application/pdf"))
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        openUri(uri)
    }

    private fun openUri(uri: Uri) {
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                closeCurrentDocument()
                val doc = PdfDocument.fromFd(pfd.detachFd())
                currentDocument = doc
                renderPool = RenderPool(doc)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun closeCurrentDocument() {
        renderPool?.clearCache()
        renderPool = null
        currentDocument?.close()
        currentDocument = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCurrentDocument()
    }
}

@Composable
fun EmptyStateScreen(
    onOpenClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "PdfCraft",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pure-Rust High-Fidelity PDF Workbench",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onOpenClicked,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open PDF Document", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
