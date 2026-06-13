package com.adarsh.hellomom.domain.usecase

import com.adarsh.hellomom.core.constants.DocumentConstants
import com.adarsh.hellomom.domain.model.UploadState
import com.adarsh.hellomom.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Validates a candidate file (extension + size), generates the storage path
 * `{firebaseUid}/{timestampSeconds}_{originalFileName}` and delegates the actual
 * upload to the [DocumentRepository]. Validation failures short-circuit with a
 * non-retryable [UploadState.Error] without ever reading the file bytes.
 */
class UploadDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    operator fun invoke(
        uid: String,
        title: String,
        originalFileName: String,
        fileSize: Long,
        extension: String,
        category: String,
        timestampSeconds: Long,
        readBytes: suspend () -> ByteArray
    ): Flow<UploadState> {
        if (uid.isBlank()) {
            return flowOf(UploadState.Error("You must be signed in to upload.", retryable = false))
        }

        val ext = extension.lowercase()
        if (ext !in DocumentConstants.ALLOWED_EXTENSIONS) {
            return flowOf(
                UploadState.Error(
                    "Unsupported file type. Allowed: ${DocumentConstants.ALLOWED_EXTENSIONS.joinToString(", ")}.",
                    retryable = false
                )
            )
        }

        if (fileSize <= 0L) {
            return flowOf(UploadState.Error("File is empty or could not be read.", retryable = false))
        }
        if (fileSize > DocumentConstants.MAX_FILE_SIZE_BYTES) {
            return flowOf(UploadState.Error("File exceeds the 20 MB limit.", retryable = false))
        }

        val storageFileName = DocumentConstants.sanitizeFileName(originalFileName)
        val storagePath = "$uid/${timestampSeconds}_$storageFileName"
        val displayName = title.trim().ifBlank { originalFileName.substringBeforeLast('.').ifBlank { storageFileName } }

        return repository.uploadDocument(
            uid = uid,
            storagePath = storagePath,
            fileName = displayName,
            category = category.ifBlank { "Other" },
            fileType = ext,
            fileSize = fileSize,
            readBytes = readBytes
        )
    }
}
