package com.adarsh.hellomom.domain.usecase

import com.adarsh.hellomom.domain.model.DocumentModel
import com.adarsh.hellomom.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * Deletes a document: removes the file from Supabase Storage and the metadata from
 * Firestore. Confirmation and list refresh are handled by the UI / ViewModel.
 */
class DeleteDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(uid: String, document: DocumentModel): Result<Unit> =
        repository.deleteDocument(uid, document)
}
