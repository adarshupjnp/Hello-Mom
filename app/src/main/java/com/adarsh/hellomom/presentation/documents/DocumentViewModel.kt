package com.adarsh.hellomom.presentation.documents

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.domain.model.DocumentModel
import com.adarsh.hellomom.domain.model.UploadState
import com.adarsh.hellomom.domain.repository.DocumentRepository
import com.adarsh.hellomom.domain.usecase.DeleteDocumentUseCase
import com.adarsh.hellomom.domain.usecase.UploadDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DocumentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository,
    private val uploadDocumentUseCase: UploadDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val roleManager: RoleManager
) : BaseViewModel<DocumentIntent, DocumentState, DocumentEffect>() {

    override fun createInitialState(): DocumentState = DocumentState()

    private var documentsJob: Job? = null
    private var activeUid: String = ""
    private var lastUpload: PendingUpload? = null

    private data class PendingUpload(val fileUri: String, val title: String, val category: String)

    init {
        handleIntent(DocumentIntent.Load)
    }

    override fun handleIntent(intent: DocumentIntent) {
        when (intent) {
            DocumentIntent.Load -> load(refreshing = false)
            DocumentIntent.Refresh -> load(refreshing = true)
            is DocumentIntent.Upload -> upload(intent.fileUri, intent.title, intent.category)
            is DocumentIntent.Update -> update(intent.document)
            is DocumentIntent.Delete -> delete(intent.document)
            is DocumentIntent.Search -> {
                setState { copy(searchQuery = intent.query) }
                applyFilter()
            }
            is DocumentIntent.FilterCategory -> {
                setState { copy(selectedCategory = intent.category) }
                applyFilter()
            }
            DocumentIntent.RetryUpload -> lastUpload?.let { upload(it.fileUri, it.title, it.category) }
            DocumentIntent.DismissUpload -> setState { copy(uploadProgress = null, uploadRetryable = false, error = null) }
        }
    }

    private fun load(refreshing: Boolean) {
        documentsJob?.cancel()
        documentsJob = viewModelScope.launch {
            setState {
                copy(
                    isLoading = !refreshing && documents.isEmpty(),
                    isRefreshing = refreshing,
                    error = null
                )
            }
            val access = roleManager.resolveAccess()
            activeUid = access.activeUserId
            setState { copy(isOwner = access.isOwner) }

            if (access.activeUserId.isBlank()) {
                setState { copy(isLoading = false, isRefreshing = false) }
                return@launch
            }

            repository.getDocuments(access.activeUserId)
                .catch { e ->
                    setState { copy(isLoading = false, isRefreshing = false, error = e.message) }
                    setEffect { DocumentEffect.ShowError(e.message ?: "Failed to load documents.") }
                }
                .collect { list ->
                    setState { copy(documents = list, isLoading = false, isRefreshing = false) }
                    applyFilter()
                }
        }
    }

    private fun applyFilter() {
        val query = uiState.value.searchQuery.trim()
        val category = uiState.value.selectedCategory
        val filtered = uiState.value.documents.filter { doc ->
            val matchesQuery = query.isBlank() ||
                doc.name.contains(query, ignoreCase = true) ||
                doc.category.contains(query, ignoreCase = true)
            val matchesCategory = category.isNullOrBlank() || doc.category == category
            matchesQuery && matchesCategory
        }
        setState { copy(filtered = filtered) }
    }

    private fun upload(fileUri: String, title: String, category: String) {
        viewModelScope.launch {
            val access = roleManager.resolveAccess()
            if (!access.isOwner) {
                setEffect { DocumentEffect.ShowError("Only the owner can upload documents.") }
                return@launch
            }
            val uid = access.activeUserId
            lastUpload = PendingUpload(fileUri, title, category)

            val uri = Uri.parse(fileUri)
            val meta = queryFileMeta(uri)

            setState { copy(uploadProgress = 0f, uploadRetryable = false, error = null) }

            val readBytes: suspend () -> ByteArray = {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Could not read the selected file.")
                }
            }

            uploadDocumentUseCase(
                uid = uid,
                title = title,
                originalFileName = meta.name,
                fileSize = meta.size,
                extension = meta.extension,
                category = category,
                timestampSeconds = System.currentTimeMillis() / 1000,
                readBytes = readBytes
            ).onEach { uploadState ->
                when (uploadState) {
                    is UploadState.Progress ->
                        setState { copy(uploadProgress = uploadState.fraction) }
                    is UploadState.Success -> {
                        lastUpload = null
                        setState { copy(uploadProgress = null, uploadRetryable = false, error = null) }
                        setEffect { DocumentEffect.ShowSuccess("Document uploaded successfully.") }
                    }
                    is UploadState.Error -> {
                        setState { copy(uploadProgress = null, uploadRetryable = uploadState.retryable, error = uploadState.message) }
                        setEffect { DocumentEffect.ShowError(uploadState.message) }
                    }
                }
            }.launchIn(viewModelScope)
        }
    }

    private fun update(document: DocumentModel) {
        viewModelScope.launch {
            val access = roleManager.resolveAccess()
            if (!access.isOwner) {
                setEffect { DocumentEffect.ShowError("Only the owner can edit documents.") }
                return@launch
            }
            repository.updateDocument(access.activeUserId, document)
                .onSuccess { setEffect { DocumentEffect.ShowSuccess("Document updated.") } }
                .onFailure { setEffect { DocumentEffect.ShowError(it.message ?: "Failed to update document.") } }
        }
    }

    private fun delete(document: DocumentModel) {
        viewModelScope.launch {
            val access = roleManager.resolveAccess()
            if (!access.isOwner) {
                setEffect { DocumentEffect.ShowError("Only the owner can delete documents.") }
                return@launch
            }
            deleteDocumentUseCase(access.activeUserId, document)
                .onSuccess { setEffect { DocumentEffect.ShowSuccess("Document deleted.") } }
                .onFailure { setEffect { DocumentEffect.ShowError(it.message ?: "Failed to delete document.") } }
        }
    }

    private data class FileMeta(val name: String, val size: Long, val extension: String)

    private fun queryFileMeta(uri: Uri): FileMeta {
        var name = "file"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        if (size <= 0L) {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { size = it.length }
            }
        }
        var ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isBlank() || ext == name.lowercase()) {
            // Fall back to mime type when the display name has no extension.
            val mime = context.contentResolver.getType(uri)
            ext = when (mime) {
                "application/pdf" -> "pdf"
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "application/msword" -> "doc"
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
                else -> ext
            }
        }
        return FileMeta(name, size, ext)
    }
}
