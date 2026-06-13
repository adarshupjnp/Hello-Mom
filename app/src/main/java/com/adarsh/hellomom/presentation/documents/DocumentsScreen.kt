package com.adarsh.hellomom.presentation.documents

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.core.constants.DocumentConstants
import com.adarsh.hellomom.domain.model.DocumentModel
import com.adarsh.hellomom.navigation.Screen
import com.adarsh.hellomom.presentation.components.FeedbackOverlay
import com.adarsh.hellomom.presentation.components.ListShimmer
import com.adarsh.hellomom.presentation.components.UiFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    navController: NavController,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var feedback by remember { mutableStateOf<UiFeedback?>(null) }
    var pendingUploadUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDownload by remember { mutableStateOf<DocumentModel?>(null) }
    var editingDocument by remember { mutableStateOf<DocumentModel?>(null) }
    var deletingDocument by remember { mutableStateOf<DocumentModel?>(null) }

    // Surface ViewModel success/error effects as the animated overlay.
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            feedback = when (effect) {
                is DocumentEffect.ShowSuccess -> UiFeedback.Success(effect.message)
                is DocumentEffect.ShowError -> UiFeedback.Error(effect.message)
            }
        }
    }

    // Pick a file to upload, then ask for a title + category.
    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingUploadUri = uri }

    // Download: let the user choose where to save, then stream the file to that location.
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destination ->
        val doc = pendingDownload
        pendingDownload = null
        if (destination != null && doc != null) {
            scope.launch {
                feedback = UiFeedback.Loading("Saving document…")
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        URL(doc.fileUrl).openStream().use { input ->
                            context.contentResolver.openOutputStream(destination)?.use { output ->
                                input.copyTo(output)
                            } ?: error("Cannot open destination.")
                        }
                    }.isSuccess
                }
                feedback = if (success) {
                    UiFeedback.Success("Saved successfully.")
                } else {
                    UiFeedback.Error("Download failed. Please try again.")
                }
            }
        }
    }

    if (pendingUploadUri != null) {
        UploadDocumentDialog(
            suggestedTitle = remember(pendingUploadUri) { queryDisplayName(context, pendingUploadUri!!).substringBeforeLast('.') },
            onDismiss = { pendingUploadUri = null },
            onConfirm = { title, category ->
                viewModel.sendIntent(DocumentIntent.Upload(pendingUploadUri!!.toString(), title, category))
                pendingUploadUri = null
            }
        )
    }

    editingDocument?.let { doc ->
        EditDocumentDialog(
            document = doc,
            onDismiss = { editingDocument = null },
            onSave = { updated ->
                viewModel.sendIntent(DocumentIntent.Update(updated))
                editingDocument = null
            }
        )
    }

    deletingDocument?.let { doc ->
        AlertDialog(
            onDismissRequest = { deletingDocument = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete document") },
            text = { Text("Delete \"${doc.name}\"? This removes the file and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendIntent(DocumentIntent.Delete(doc))
                    deletingDocument = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingDocument = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documents") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            // Family members are read-only: they can view & download but not upload.
            if (state.isOwner && state.uploadProgress == null) {
                ExtendedFloatingActionButton(
                    onClick = { pickFileLauncher.launch(DocumentConstants.PICKER_MIME_TYPES) },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    text = { Text("Upload") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Upload progress banner.
                state.uploadProgress?.let { progress ->
                    UploadProgressBanner(progress = progress)
                }

                // Search.
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.sendIntent(DocumentIntent.Search(it)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.sendIntent(DocumentIntent.Search("")) }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    placeholder = { Text("Search documents") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Category filter.
                CategoryFilterRow(
                    selected = state.selectedCategory,
                    onSelect = { viewModel.sendIntent(DocumentIntent.FilterCategory(it)) }
                )

                // Content with pull-to-refresh.
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.sendIntent(DocumentIntent.Refresh) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when {
                        state.isLoading -> LoadingState()
                        state.error != null && state.documents.isEmpty() ->
                            ErrorState(message = state.error!!, onRetry = { viewModel.sendIntent(DocumentIntent.Load) })
                        state.filtered.isEmpty() -> EmptyState(
                            hasDocuments = state.documents.isNotEmpty(),
                            isOwner = state.isOwner
                        )
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.filtered, key = { it.id }) { doc ->
                                DocumentItem(
                                    document = doc,
                                    isOwner = state.isOwner,
                                    onOpen = { openDocument(navController, doc) },
                                    onDownload = {
                                        pendingDownload = doc
                                        saveFileLauncher.launch(suggestedFileName(doc))
                                    },
                                    onShare = { shareDocument(context, doc) },
                                    onEdit = { editingDocument = doc },
                                    onDelete = { deletingDocument = doc }
                                )
                            }
                        }
                    }
                }
            }

            FeedbackOverlay(feedback = feedback, onDismiss = { feedback = null })
        }
    }
}

@Composable
private fun UploadProgressBanner(progress: Float) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Uploading… ${(progress * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().clip(CircleShape)
            )
        }
    }
}

@Composable
private fun CategoryFilterRow(selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") }
            )
        }
        items(DocumentConstants.CATEGORIES) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(category) }
            )
        }
    }
}

@Composable
private fun DocumentItem(
    document: DocumentModel,
    isOwner: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForType(document.fileType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = document.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                val date = document.uploadedAt?.toDate()?.let { dateFormat.format(it) } ?: "—"
                Text(
                    text = "$date  •  ${DocumentConstants.formatSize(document.fileSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                        onClick = { menuExpanded = false; onOpen() }
                    )
                    DropdownMenuItem(
                        text = { Text("Download") },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                        onClick = { menuExpanded = false; onDownload() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { menuExpanded = false; onShare() }
                    )
                    // Edit / delete are owner-only.
                    if (isOwner) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    ListShimmer()
}

@Composable
private fun EmptyState(hasDocuments: Boolean, isOwner: Boolean) {
    // Needs to be scrollable so pull-to-refresh works on an empty list.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.fillParentMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.FolderOff,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (hasDocuments) "No matching documents" else "No documents yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        hasDocuments -> "Try a different search or category filter."
                        isOwner -> "Tap Upload to add your first document."
                        else -> "Documents shared by the owner will appear here."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun UploadDocumentDialog(
    suggestedTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String) -> Unit
) {
    var title by remember { mutableStateOf(suggestedTitle) }
    var category by remember { mutableStateOf(DocumentConstants.CATEGORIES.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DocumentConstants.CATEGORIES) { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), category) },
                enabled = title.isNotBlank()
            ) { Text("Upload") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditDocumentDialog(
    document: DocumentModel,
    onDismiss: () -> Unit,
    onSave: (DocumentModel) -> Unit
) {
    var title by remember { mutableStateOf(document.name) }
    var category by remember { mutableStateOf(document.category.ifBlank { DocumentConstants.CATEGORIES.first() }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DocumentConstants.CATEGORIES) { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(document.copy(name = title.trim(), category = category)) },
                enabled = title.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun iconForType(fileType: String): ImageVector = when (DocumentConstants.typeOf(fileType)) {
    DocumentConstants.DocType.IMAGE -> Icons.Default.Image
    DocumentConstants.DocType.PDF -> Icons.Default.PictureAsPdf
    DocumentConstants.DocType.DOC -> Icons.Default.Description
    DocumentConstants.DocType.OTHER -> Icons.Default.InsertDriveFile
}

private fun suggestedFileName(document: DocumentModel): String {
    val base = DocumentConstants.sanitizeFileName(document.name).substringBeforeLast('.')
    val ext = document.fileType.lowercase().ifBlank { "" }
    return if (ext.isBlank()) base else "$base.$ext"
}

private fun openDocument(navController: NavController, document: DocumentModel) {
    navController.navigate(
        Screen.DocumentDetails.createRoute(
            name = document.name,
            fileType = document.fileType,
            url = document.fileUrl
        )
    )
}

private fun shareDocument(context: android.content.Context, document: DocumentModel) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, document.name)
        putExtra(Intent.EXTRA_TEXT, "${document.name}\n${document.fileUrl}")
    }
    context.startActivity(Intent.createChooser(intent, "Share document"))
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    var name = "document"
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) {
                cursor.getString(index)?.let { name = it }
            }
        }
    }
    return name
}
