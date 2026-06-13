package com.adarsh.hellomom.domain.model

/**
 * Progress/result of a document upload, emitted as a stream so the UI can show a
 * progress bar and react to success/failure.
 */
sealed interface UploadState {
    /** [fraction] is 0f..1f. */
    data class Progress(val fraction: Float) : UploadState
    data class Success(val document: DocumentModel) : UploadState
    /** [retryable] is false for validation errors (wrong type / too large). */
    data class Error(val message: String, val retryable: Boolean = true) : UploadState
}
