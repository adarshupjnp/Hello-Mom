package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.domain.model.DocumentModel
import com.adarsh.hellomom.domain.model.UploadState
import kotlinx.coroutines.flow.Flow

/**
 * Document Management repository.
 *
 * Metadata lives in Firestore (`users/{uid}/documents`); the actual file lives in the
 * public Supabase Storage bucket. Reads are real-time via a Firestore snapshot [Flow].
 */
interface DocumentRepository {

    /** Real-time stream of [uid]'s documents, newest first. */
    fun getDocuments(uid: String): Flow<List<DocumentModel>>

    /**
     * Upload a file to Storage then persist its metadata to Firestore, streaming
     * [UploadState] progress/result. [readBytes] is invoked lazily so callers can defer
     * reading large files until validation has passed.
     */
    fun uploadDocument(
        uid: String,
        storagePath: String,
        fileName: String,
        category: String,
        fileType: String,
        fileSize: Long,
        readBytes: suspend () -> ByteArray
    ): Flow<UploadState>

    /** Update editable metadata (name, category) in Firestore. The stored file is untouched. */
    suspend fun updateDocument(uid: String, document: DocumentModel): Result<Unit>

    /** Delete the file from Storage and its metadata from Firestore. */
    suspend fun deleteDocument(uid: String, document: DocumentModel): Result<Unit>
}
