package com.adarsh.hellomom.presentation.documents

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.domain.model.DocumentModel

sealed class DocumentIntent : UiIntent {
    object Load : DocumentIntent()
    object Refresh : DocumentIntent()
    data class Upload(val fileUri: String, val title: String, val category: String) : DocumentIntent()
    data class Update(val document: DocumentModel) : DocumentIntent()
    data class Delete(val document: DocumentModel) : DocumentIntent()
    data class Search(val query: String) : DocumentIntent()
    data class FilterCategory(val category: String?) : DocumentIntent()
    object RetryUpload : DocumentIntent()
    object DismissUpload : DocumentIntent()
}

data class DocumentState(
    val documents: List<DocumentModel> = emptyList(),
    val filtered: List<DocumentModel> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isOwner: Boolean = false,
    /** Non-null while an upload is in flight: 0f..1f. */
    val uploadProgress: Float? = null,
    /** True when the last upload failed and can be retried. */
    val uploadRetryable: Boolean = false,
    val error: String? = null
) : UiState

sealed class DocumentEffect : UiEffect {
    data class ShowSuccess(val message: String) : DocumentEffect()
    data class ShowError(val message: String) : DocumentEffect()
}
