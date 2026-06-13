package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.remote.supabase.SupabaseStorageManager
import com.adarsh.hellomom.domain.model.DocumentModel
import com.adarsh.hellomom.domain.model.UploadState
import com.adarsh.hellomom.domain.repository.DocumentRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storageManager: SupabaseStorageManager
) : DocumentRepository {

    private fun collection(uid: String) =
        firestore.collection("users").document(uid).collection("documents")

    override fun getDocuments(uid: String): Flow<List<DocumentModel>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = collection(uid)
            .orderBy("uploadedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    SyncLogger.error("LISTEN documents failed uid=$uid", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(DocumentModel::class.java).orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override fun uploadDocument(
        uid: String,
        storagePath: String,
        fileName: String,
        category: String,
        fileType: String,
        fileSize: Long,
        readBytes: suspend () -> ByteArray
    ): Flow<UploadState> = channelFlow {
        try {
            send(UploadState.Progress(0f))

            val bytes = readBytes()
            send(UploadState.Progress(0.15f))

            // 1. Upload the file to Supabase Storage.
            storageManager.upload(storagePath, bytes)
            send(UploadState.Progress(0.8f))

            // 2. Generate the public URL.
            val publicUrl = storageManager.publicUrl(storagePath)

            // 3. Save metadata to Firestore.
            val docRef = collection(uid).document()
            val model = DocumentModel(
                id = docRef.id,
                name = fileName,
                category = category,
                fileUrl = publicUrl,
                storagePath = storagePath,
                fileType = fileType,
                fileSize = fileSize,
                uploadedAt = Timestamp.now()
            )
            docRef.set(model).await()

            SyncLogger.firebaseWrite(
                "ADD document",
                "users/$uid/documents/${docRef.id}",
                "name=$fileName category=$category size=$fileSize path=$storagePath"
            )

            send(UploadState.Progress(1f))
            send(UploadState.Success(model))
        } catch (e: Exception) {
            SyncLogger.error("UPLOAD document failed path=$storagePath", e)
            send(UploadState.Error(e.message ?: "Upload failed. Please try again."))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun updateDocument(uid: String, document: DocumentModel): Result<Unit> {
        return try {
            collection(uid).document(document.id)
                .update(mapOf("name" to document.name, "category" to document.category))
                .await()
            SyncLogger.firebaseWrite(
                "EDIT document",
                "users/$uid/documents/${document.id}",
                "name=${document.name} category=${document.category}"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("EDIT document failed id=${document.id}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteDocument(uid: String, document: DocumentModel): Result<Unit> {
        return try {
            // 1. Delete the file from Supabase Storage (best-effort; metadata still removed).
            if (document.storagePath.isNotBlank()) {
                runCatching { storageManager.delete(document.storagePath) }
                    .onFailure { SyncLogger.warn("Storage delete failed path=${document.storagePath}", it) }
            }
            // 2. Delete the metadata from Firestore.
            collection(uid).document(document.id).delete().await()
            SyncLogger.firebaseWrite("DELETE document", "users/$uid/documents/${document.id}", "removed")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("DELETE document failed id=${document.id}", e)
            Result.failure(e)
        }
    }
}
